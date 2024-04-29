# RuneMate Gradle Plugin

This plugin provides a set of integration utilities to assist development on the RuneMate platform. 

## Usage

Simply add the following to your Gradle buildscript to get started:

```kotlin
plugins {
    id("com.runemate.gradle-plugin") version "1.0"
}

runemate {
    devMode = true
    autoLogin = true
}
```

### Configuration
The full list of configuration options is below:

| Option                     | Description                                                            | Default                                            |
|----------------------------|------------------------------------------------------------------------|----------------------------------------------------|
| autoLogin                  | Tells the client to attempt automatic login                            | false                                              |
| devMode                    | Tells the client to launch in developer mode                           | true                                               |
| apiVersion                 | Tells Gradle which version of runemate-game-api to fetch from Maven    | + (latest)                                         |
| clientVersion              | Tells Gradle which version of runemate-client to fetch from Maven      | + (latest)                                         |
| botDirectories             | Tells the client which directories to scan for bots                    | `$projectDir/build/libs` (project build directory) |
| allowsExternalDependencies | Tells Gradle to allow dependency resolution for external dependencies. | false                                              |

### Launching the client
The plugin adds the `runClient` task to your Gradle project, this will launch the client using the configuration as described above.

Type the following command:
```
./gradlew runClient
```

### Manifest Validation
The plugin adds the `validateManifests` task to your Gradle project, this will scan your projects for manifests and run validation against them.

## Building the plugin

To build the plugin, just type the following command:

```
./gradlew build
```