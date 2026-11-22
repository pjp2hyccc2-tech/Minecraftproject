# ArchiQuest build output

GitHub Actions writes the latest build result here:

- `archiquest-*.jar` : the mod (put it in the `mods` folder of a Forge 1.20.1 profile)
- `status.txt`       : SUCCESS / FAILED and the commit that was built
- `build-log.txt`    : full Gradle log of the last build
- `errors.txt`       : compiler errors only (when the build failed)
