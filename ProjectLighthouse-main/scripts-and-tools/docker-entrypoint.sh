#!/bin/sh

log() {
    type="$1"; shift
    printf '%s [%s] [Entrypoint]: %s\n' "$(date -Iseconds)" "$type" "$*"
}

log Note "Entrypoint script for Lighthouse $SERVER started"

if [ ! -d "/lighthouse/data" ]; then
    log Note "Creating data directory"
    mkdir -p "/lighthouse/data"
fi

owner=$(stat -c "%U %G" /lighthouse/data)
if [ "$owner" != "lighthouse lighthouse" ]; then
    log Note "Changing ownership of data directory"
    chown -R lighthouse:lighthouse /lighthouse/data
fi

if [ -d "/lighthouse/temp" ]; then
    log Note "Copying temp directory to data"
    cp -rn /lighthouse/temp/* /lighthouse/data
    rm -rf /lighthouse/temp
fi

if [ ! -f "/lighthouse/data/lighthouse.yml" ] && [ -f "/lighthouse/default-lighthouse.yml" ]; then
    log Note "Copying default lighthouse.yml into data"
    cp /lighthouse/default-lighthouse.yml /lighthouse/data/lighthouse.yml
fi

# Update external URLs with EXTERNAL_HOST if set
host="${EXTERNAL_HOST:-192.168.100.6}"
config="/lighthouse/data/lighthouse.yml"

# PaaS / "singleserver" mode (Render.com, Railway, Fly.io, ...):
# those platforms tell the app which port to bind with $PORT and only route to that one,
# so the GameApi listener has to move with it. Program.cs calls
#     app.UseUrls(LighthouseServerConfiguration.Get().GameApiListenUrl);
# and UseUrls() OVERRIDES ASPNETCORE_URLS, so exporting ASPNETCORE_URLS is not enough --
# the port has to be written into the config file itself.
if [ -f "$config" ]; then
    log Note "Ensuring external URLs use $host"
    external_url="${EXTERNAL_URL:-http://$host:10060}"
    game_api_external_url="${GAME_API_EXTERNAL_URL:-http://$host:10061/LITTLEBIGPLANETPS3_XML}"
    sed -i "s|externalUrl:.*|externalUrl: \"$external_url\"|g" "$config"
    sed -i "s|gameApiExternalUrl:.*|gameApiExternalUrl: \"$game_api_external_url\"|g" "$config"

    if [ -n "$PORT" ]; then
        log Note "Binding GameApi listener to port $PORT (from \$PORT)"
        sed -i "s|gameApiListenUrl:.*|gameApiListenUrl: \"http://0.0.0.0:$PORT\"|g" "$config"
    fi
fi

# Start server
log Note "Startup tasks finished, starting $SERVER..."
cd /lighthouse/data || exit

case "$SERVER" in
    Api|API)
        exe="ProjectLighthouse.Servers.API.dll"
        ;;
    GameApi)
        exe="ProjectLighthouse.Servers.GameApi.dll"
        ;;
    Website)
        exe="ProjectLighthouse.Servers.Website.dll"
        ;;
    *)
        exe="ProjectLighthouse.Servers.$SERVER.dll"
        ;;
esac

exec su-exec lighthouse:lighthouse dotnet /lighthouse/app/"$exe"

exit $?