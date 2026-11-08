#!/usr/bin/env python3
"""
Uploads the StreamMind sample video library to your own Firebase Storage bucket.

Why this exists: the library previously pointed at third-party test buckets
(Google's gtv-videos-bucket, html5demos.com, etc.) that quietly go dead or get
permission-locked with no warning, because serving your app's content was
never their job. This script re-hosts the same films on infrastructure you
control, so playback stops depending on someone else's bucket staying up.

WHAT IT DOES
  1. Downloads each source video (verified-live Internet Archive mirrors of
     Blender Foundation / Blender Studio open movies, all Creative Commons)
     to a local temp folder.
  2. Uploads each one to  gs://<your-bucket>/videos/<id>.mp4
  3. Also uploads the matching transcript JSON (already bundled in the app at
     app/src/main/assets/transcripts/<id>.json) to
     gs://<your-bucket>/transcripts/<id>.json, purely for your own reference —
     the app keeps reading transcripts from the local asset, since that part
     was never actually broken and doesn't need a network round-trip.
  4. Makes each uploaded file publicly readable and prints the resulting
     https://storage.googleapis.com/... URLs, plus a ready-to-paste Kotlin
     snippet for VideoLibrary.kt.

BEFORE YOU RUN THIS

  1. Your Firebase project must be on the Blaze (pay-as-you-go) plan.
     As of Feb 2026, Firebase requires Blaze for ANY Cloud Storage usage —
     even a single free-tier file. You still won't be billed as long as you
     stay under the "Always Free" tier (5 GB-months storage, 100 GB egress to
     North America per month), but a billing account/card must be linked.
     Firebase Console -> your project -> gear icon -> Usage and billing.

  2. Create a service account key:
     Firebase Console -> Project Settings -> Service Accounts ->
     "Generate new private key" -> save the JSON file somewhere local
     (do NOT commit it to git).

  3. Install dependencies:
     pip install firebase-admin requests

USAGE
  python upload_videos_to_firebase.py \
      --credentials /path/to/serviceAccountKey.json \
      --bucket your-project-id.firebasestorage.app \
      --assets-dir /path/to/StreamMind/app/src/main/assets/transcripts

  (find your bucket name in Firebase Console -> Storage -> it's shown at the
  top, e.g. "my-app-12345.firebasestorage.app")
"""

import argparse
import pathlib
import sys
import tempfile

import requests
import firebase_admin
from firebase_admin import credentials, storage

# id -> (source URL, human title) -- verified-live Internet Archive mirrors,
# all Creative Commons licensed Blender Foundation / Blender Studio films.
VIDEOS = {
    "sample_2": (
        "https://archive.org/download/Sintel_201709/Elephants%20Dream.mp4",
        "Elephants Dream",
    ),
    "sample_3": (
        "https://archive.org/download/Sintel_201709/Sintel.mp4",
        "Sintel",
    ),
    "sample_4": (
        "https://archive.org/download/Sintel_201709/Tears%20of%20Steel.mp4",
        "Tears of Steel",
    ),
    "sample_5": (
        "https://archive.org/download/springopenmovie/springopenmovie.mp4",
        "Spring",
    ),
    # sample_1 (Big Buck Bunny) is left off this list on purpose -- its current
    # exoplayer-test-media-0 URL still works today, so there's nothing to fix.
    # Add it here the same way as the others if you'd rather self-host it too.
}


def download(url: str, dest: pathlib.Path) -> None:
    print(f"  downloading {url}")
    with requests.get(url, stream=True, timeout=60) as response:
        response.raise_for_status()
        total = int(response.headers.get("content-length", 0))
        written = 0
        with open(dest, "wb") as f:
            for chunk in response.iter_content(chunk_size=1024 * 1024):
                f.write(chunk)
                written += len(chunk)
                if total:
                    pct = written * 100 // total
                    print(f"\r  {pct:3d}% ({written // (1024 * 1024)} MB)", end="", flush=True)
        print()


def upload_public(bucket, local_path: pathlib.Path, remote_path: str) -> str:
    blob = bucket.blob(remote_path)
    print(f"  uploading -> gs://{bucket.name}/{remote_path}")
    blob.upload_from_filename(str(local_path))
    blob.make_public()
    return blob.public_url


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--credentials", required=True, help="Path to Firebase service account JSON key")
    parser.add_argument("--bucket", required=True, help="Firebase Storage bucket name, e.g. my-app.firebasestorage.app")
    parser.add_argument(
        "--assets-dir",
        default=None,
        help="Path to app/src/main/assets/transcripts (optional -- if given, also mirrors transcript JSON to Storage for reference)",
    )
    args = parser.parse_args()

    cred = credentials.Certificate(args.credentials)
    firebase_admin.initialize_app(cred, {"storageBucket": args.bucket})
    bucket = storage.bucket()

    results: dict[str, str] = {}

    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = pathlib.Path(tmp)

        for video_id, (url, title) in VIDEOS.items():
            print(f"\n[{video_id}] {title}")
            local_file = tmp_path / f"{video_id}.mp4"
            download(url, local_file)
            public_url = upload_public(bucket, local_file, f"videos/{video_id}.mp4")
            results[video_id] = public_url
            print(f"  done -> {public_url}")

            if args.assets_dir:
                transcript_path = pathlib.Path(args.assets_dir) / f"{video_id}.json"
                if transcript_path.exists():
                    upload_public(bucket, transcript_path, f"transcripts/{video_id}.json")
                else:
                    print(f"  (no local transcript found at {transcript_path}, skipping)")

    print("\n" + "=" * 60)
    print("Done. Paste these into VideoLibrary.kt (videoUrl fields):")
    print("=" * 60)
    for video_id, url in results.items():
        print(f'  "{video_id}" -> videoUrl = "{url}"')

    return 0


if __name__ == "__main__":
    sys.exit(main())
