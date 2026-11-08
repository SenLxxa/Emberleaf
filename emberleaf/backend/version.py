"""The launcher's version: shown in the window, handed to Minecraft and sent as user agent."""
APP_VERSION = "0.0.8"
APP_CHANNEL = "Alpha"
APP_LABEL = f"{APP_VERSION} {APP_CHANNEL}"  # "0.0.2 Alpha"
USER_AGENT = f"emberleaf-launcher/{APP_VERSION}"

# The Emberleaf website: new versions come from there (the owner uploads them on <SITE_URL>/upload).
# The same Cloudflare worker is also the Lagerfeuer (friends server).
SITE_URL = "https://lagerfeuer.emberleaf-server.workers.dev"
OWNER_NAME = "SenLxca"  # Minecraft name of the owner (gets the founder badge)

# The friends server everyone uses unless they enter another one in the settings
LAGERFEUER_URL = SITE_URL
