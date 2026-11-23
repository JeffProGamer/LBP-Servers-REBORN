# This repository contains sticker tooling, not the Refresh C# source.
# Fetch an immutable, audited revision and its pinned Bunkum submodule.
FROM mcr.microsoft.com/dotnet/sdk:9.0-bookworm-slim AS build
RUN apt-get update \
    && apt-get install -y --no-install-recommends git ca-certificates \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /src
ARG CACHEBUST=17
RUN git init . \
    && git remote add origin https://github.com/JeffProGamer/Refresh.git \
    && git fetch --depth 1 origin main \
    && git checkout --detach FETCH_HEAD \
    && git submodule update --init --recursive --depth 1 \
    # Patch Bunkum to accept PS4's non-standard HTTP version strings
    && sed -i 's/throw new NotSupportedException(version.ToString());/httpVersion = HttpVersion.Http1_1;/' External/Bunkum/Bunkum.Protocols.Http/Socket/SocketHttpListener.cs \
    # Patch Bunkum to accept PS4's non-standard HTTP methods instead of rejecting them
    && sed -i 's/throw new Exception("Rejected request that sent invalid method " + method.ToString());/context.Method = new Method(new string(method));/' External/Bunkum/Bunkum.Protocols.Http/Socket/SocketHttpListener.cs \
    # Patch Bunkum to accept PS4 requests without a Host header
    && sed -i 's/throw new Exception("Rejected request without Host header");/context.RequestHeaders["Host"] = "lbpreborn.onrender.com";/' External/Bunkum/Bunkum.Protocols.Http/Socket/SocketHttpListener.cs
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
