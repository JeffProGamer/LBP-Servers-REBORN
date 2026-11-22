# This repository contains sticker tooling, not the Refresh C# source.
# Fetch an immutable, audited revision and its pinned Bunkum submodule.
FROM mcr.microsoft.com/dotnet/sdk:9.0-bookworm-slim AS build
RUN apt-get update \
    && apt-get install -y --no-install-recommends git ca-certificates \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /src
RUN git init . \
    && git remote add origin https://github.com/JeffProGamer/Refresh.git \
    && git fetch --depth 1 origin 738a79b89e721057673cc300ffe3c3f654d03297 \
    && git checkout --detach FETCH_HEAD \
    && git submodule update --init --recursive --depth 1
RUN dotnet publish Refresh.GameServer/Refresh.GameServer.csproj \
    --configuration Release --output /out --no-self-contained /p:Version=0.0.0
FROM mcr.microsoft.com/dotnet/runtime:9.0-bookworm-slim AS final
RUN groupadd --gid 1001 refresh \
    && useradd --uid 1001 --gid refresh --create-home refresh \
    && mkdir -p /refresh/app /refresh/data \
    && chown -R refresh:refresh /refresh
WORKDIR /refresh/data
COPY --from=build --chown=refresh:refresh /out/ /refresh/app/
COPY --chown=refresh:refresh deploy/render/bunkum.json deploy/render/refreshGameServer.json /refresh/data/
ENV BUNKUM_DATA_FOLDER=/refresh/data
# Bunkum reads ListenPort from bunkum.json, not PORT. Keep both at 10000.
ENV PORT=10000
EXPOSE 10000
USER refresh
ENTRYPOINT ["dotnet", "/refresh/app/Refresh.GameServer.dll"]
