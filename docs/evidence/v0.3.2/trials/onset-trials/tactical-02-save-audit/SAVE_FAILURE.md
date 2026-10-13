Onset TACTICAL, actual runtime WatchCampaign.version = 0.3.1.

The native tick driver completed all 20 original objectives and won naturally at 2990.1333 simulated seconds. The backend awarded one tactical star, keeping the player's previously earned three normal stars; the result reports four of five stars. Minimum core health was 1.0 and 226 buildings were completed. No resource, score, objective, wave, damage, or victory was assigned by the driver.

`result.txt`, `runtime.txt`, `trial.csv`, `driver-source.java`, and `rules-before-save.json` were written before the native final-world save. `SaveIO.write(end.msav)` then failed. The partial `end.msav` is not a validated, loadable final world and must not be used as a completed save artifact.

The unaltered console is `../tactical-02-save-audit-console.txt`. Its exception is `UTFDataFormatException: encoded string ... too long: 71697 bytes`, inside the native `SaveVersion.writeMeta` → `SaveFileReader.writeStringMap` → `DataOutputStream.writeUTF` path. The native single-string limit is 65535 modified-UTF bytes.

`rules-volume.txt` accounts for the exact original serialized text length without hashes or checksums. The 1008 stars tags occupy 63305 modified-UTF bytes; removing all stars tags from a parsed diagnostic copy leaves 8394 bytes of rules. The read-only accounting source is `trial-work/OnsetRulesVolume.java`.

This run proves native tactical victory and score persistence. It does not prove successful final-world saving. The parent agent is handling the product persistence repair. This failed v0.3.1 scene is retained unchanged for regression evidence.
