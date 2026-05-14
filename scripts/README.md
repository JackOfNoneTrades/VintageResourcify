# Upstream flattening

`preproc_1809.py` resolves the ReplayMod preprocess directives in
[DeDiamondPro/Resourcify](https://github.com/DeDiamondPro/Resourcify) for the
1.8.9-forge node, producing a static source tree we can crib from when
porting features to 1.7.10.

## Refresh workflow

```sh
git -C /tmp/resourcify-clone fetch origin legacy
git -C /tmp/resourcify-clone checkout legacy
rm -rf _upstream_flat
python3 scripts/preproc_1809.py /tmp/resourcify-clone/src _upstream_flat
git -C /tmp/resourcify-clone rev-parse HEAD > _upstream_flat/.SYNCED_COMMIT
```

After re-flattening, diff `_upstream_flat/` against the last sync to find
what changed upstream, then translate file-by-file.

## Current sync point

Run `cat _upstream_flat/.SYNCED_COMMIT` (kept fresh by the refresh workflow).
