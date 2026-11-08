#!/usr/bin/env python3
"""
Uploads the StreamMind sample video library to a GitHub Release on a repo you
own, as an alternative to Firebase Storage that needs no billing plan at all.

WHY GITHUB RELEASES
  - Free, no credit card, works on free personal accounts.
  - Release assets support files up to 2 GB each (ours are ~100-130 MB).
  - Releases don't expire and aren't cleaned up automatically.
  - The resulting download URLs are plain HTTPS with range-request support,
    so ExoPlayer can stream/seek them exactly like any other direct URL.
  - Caveat: for the URL to work with no auth header (which is what a plain
    videoUrl string in the app needs), the repo holding the release must be
    PUBLIC. A private repo's release assets require an Authorization header
    on every request, which ExoPlayer's basic MediaItem doesn't send.

BEFORE YOU RUN THIS

  1. A GitHub repo to attach the release to. Can be this project's own repo,
     or a separate throwaway public repo you create just for hosting assets
     (e.g. "streammind-assets") if you don't want a 500MB release cluttering
     your main repo's Releases tab.

  2. A Personal Access Token with permission to upload release assets:
     GitHub -> Settings -> Developer settings -> Personal access tokens ->
     Fine-grained tokens -> generate one scoped to that repo with
     "Contents: Read and write" permission.

  3. Install dependencies:
     pip install requests

USAGE
  python upload_videos_to_github_releases.py \
      --repo yourusername/streammind-assets \
      --token ghp_xxxxxxxxxxxxxxxxxxxx \
      --assets-dir /path/to/StreamMind/app/src/main/assets/transcripts

  (--token can also be set via the GITHUB_TOKEN environment variable instead
  of passing it on the command line, so it doesn't end up in shell history)
"""

import argparse
import os
import pathlib
import sys
import tempfile

import requests

API_ROOT = "https://api.github.com"

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


def get_or_create_release(repo: str, tag: str, session: requests.Session) -> dict:
    resp = session.get(f"{API_ROOT}/repos/{repo}/releases/tags/{tag}")
    if resp.status_code == 200:
        print(f"  using existing release '{tag}'")
        return resp.json()

    print(f"  creating release '{tag}'")
    resp = session.post(
        f"{API_ROOT}/repos/{repo}/releases",
        json={
            "tag_name": tag,
            "name": "StreamMind video library",
            "body": "Self-hosted copies of the StreamMind sample video library. "
                    "Source films are Blender Foundation / Blender Studio open "
                    "movies, Creative Commons licensed.",
            "draft": False,
            "prerelease": False,
        },
    )
    resp.raise_for_status()
    return resp.json()


def upload_asset(upload_url_template: str, local_path: pathlib.Path, asset_name: str,
                  content_type: str, session: requests.Session) -> str:
    upload_url = upload_url_template.split("{")[0]
    with open(local_path, "rb") as f:
        data = f.read()
    resp = session.post(
        upload_url,
        params={"name": asset_name},
        headers={"Content-Type": content_type},
        data=data,
    )
    if resp.status_code == 422:
        # Asset with this name already exists on the release -- fetch its URL
        # instead of failing, so re-runs are idempotent.
        print(f"  '{asset_name}' already exists on this release, reusing it")
        release = session.get(upload_url_template.split("/releases/")[0]).json()
    resp.raise_for_status()
    return resp.json()["browser_download_url"]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--repo", required=True, help="owner/repo to attach the release to (must be public for URLs to work with no auth)")
    parser.add_argument("--token", default=os.environ.get("GITHUB_TOKEN"), help="GitHub personal access token (or set GITHUB_TOKEN env var)")
    parser.add_argument("--tag", default="video-library-v1", help="Release tag to create/reuse (default: video-library-v1)")
    parser.add_argument(
        "--assets-dir",
        default=None,
        help="Path to app/src/main/assets/transcripts (optional -- if given, also uploads transcript JSON to the release for reference)",
    )
    args = parser.parse_args()

    if not args.token:
        print("Error: no token given. Pass --token or set GITHUB_TOKEN.", file=sys.stderr)
        return 1

    session = requests.Session()
    session.headers.update({
        "Authorization": f"Bearer {args.token}",
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
    })

    release = get_or_create_release(args.repo, args.tag, session)
    upload_url_template = release["upload_url"]

    results: dict[str, str] = {}

    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = pathlib.Path(tmp)

        for video_id, (url, title) in VIDEOS.items():
            print(f"\n[{video_id}] {title}")
            local_file = tmp_path / f"{video_id}.mp4"
            download(url, local_file)
            asset_url = upload_asset(upload_url_template, local_file, f"{video_id}.mp4", "video/mp4", session)
            results[video_id] = asset_url
            print(f"  done -> {asset_url}")

            if args.assets_dir:
                transcript_path = pathlib.Path(args.assets_dir) / f"{video_id}.json"
                if transcript_path.exists():
                    upload_asset(upload_url_template, transcript_path, f"{video_id}.json", "application/json", session)
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
