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

### Example service using systemd

#### /etc/systemd/system/minecraft-update.service
```
[Unit]
Description=Minecraft bedrock server update service
StartLimitIntervalSec=0
Wants=network-online.target

[Service]
Type=oneshot
Restart=no
RestartSec=1
User=david
ExecStart=/home/david/mbsau /home/david/minecraft-server /home/david/backups

[Install]
WantedBy=multi-user.target
```

#### /etc/systemd/system/minecraft.service
```
[Unit]
Description=Minecraft Bedrock Server
Wants=minecraft-update.service
After=minecraft-update.service

[Service]
Type=forking
User=david
Group=david
ExecStart=/usr/bin/bash /home/david/minecraft-server/run.sh
ExecStop=/usr/bin/bash /home/david/minecraft-server/stop.sh
WorkingDirectory=/home/david/minecraft-server/
Restart=always
TimeoutStartSec=600

[Install]
WantedBy=multi-user.target
```

#### run.sh
``` bash
#!/usr/bin/env bash

SERVER_PATH=/home/david/minecraft-server/

/usr/bin/screen -dmS mcbedrock /bin/bash -c "LD_LIBRARY_PATH=$SERVER_PATH ${SERVER_PATH}bedrock_server"
/usr/bin/screen -rD mcbedrock -X multiuser on
/usr/bin/screen -rD mcbedrock -X acladd root
```

#### stop.sh
```bash
#!/usr/bin/env bash

/usr/bin/screen -Rd mcbedrock -X stuff "stop \r"
```