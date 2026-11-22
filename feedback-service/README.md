# HaloTV feedback migration

New feedback belongs in the [HaloTV GitHub issue form](https://github.com/Null9797/halo-tv/issues/new?template=bug_report.yml). The old submission and supplement routes return HTTP 410. The old web page points to GitHub.

The existing authenticated archive at `/feedback/admin.html` supports read-only access under the original 30-day viewing window. Its D1 database and administrator authentication stay in place. Reports remain private and are never copied into GitHub. The retired submit flow no longer runs its opportunistic expired-row cleanup.

`deploy/website` mirrors the feedback pages and the isolated Pages worker. Run `python3 feedback-service/build-deploy.py` after editing the worker. The production deployment is maintained in the publication repository. No credentials belong in the application or repository.

Run `node --test feedback-service/handler.test.mjs` with Node 24.
