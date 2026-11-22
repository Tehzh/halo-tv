"""Refresh the read-only archive worker in the Pages deployment overlay."""
from pathlib import Path
import shutil

here = Path(__file__).resolve().parent
shutil.copyfile(here / "handler.mjs", here / "deploy/website/_worker.js")
print("Ready: deploy/website contains the GitHub feedback page and read-only archive.")
