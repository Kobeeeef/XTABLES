#!/bin/bash
# Variables
SERVICE_NAME="xtables"
SERVICE_DESC="XTABLES Service"
JAR_URL="https://github.com/Kobeeeef/XTABLES/releases/download/v5.0.0/XTABLES.jar"
INSTALL_DIR="/opt/xtables"
JAR_PATH="$INSTALL_DIR/XTABLES.jar"
SYSTEMD_FILE="/lib/systemd/system/$SERVICE_NAME.service"

# Create installation directory
mkdir -p "$INSTALL_DIR"

# Download the JAR file
curl -L "$JAR_URL" -o "$JAR_PATH"

# Ensure the JAR file is executable
chmod +x "$JAR_PATH"

# Create the systemd service file
cat > "$SYSTEMD_FILE" <<EOF
[Unit]
Description=$SERVICE_DESC
After=network.target

[Service]
ExecStart=java -jar $JAR_PATH --additional-features=true
Restart=always
RestartSec=3
StandardOutput=syslog
StandardError=syslog
SyslogIdentifier=$SERVICE_NAME

[Install]
WantedBy=multi-user.target
EOF

# Reload systemd, enable and start the service
systemctl daemon-reload
systemctl enable "$SERVICE_NAME.service"
systemctl start "$SERVICE_NAME.service"

echo "Service $SERVICE_NAME has been set up, started!"
