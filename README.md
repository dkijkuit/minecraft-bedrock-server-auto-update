# Minecraft Bedrock Server Auto Update (MBSAU)

Because Minecraft does not provide an easy update mechanism for the Bedrock server, this repository addresses this issue.
A native binary is built using GraalVM allowing a dependency-less installation. The only thing you need to do
is to schedule the binary before starting the server.

## Usage
Provide a backup location and the current server installation directory.
```bash
mbsau ~/minecraft-bedrock ~/backup
```

## Building
Make sure to have installed GraalVM and the `native-image` plugin. Build native binary using Maven:
```bash
mvn clean -Pnative package
```