"""The launcher's version: shown in the window, handed to Minecraft and sent as user agent."""
APP_VERSION = "0.0.7"
APP_CHANNEL = "Alpha"
APP_LABEL = f"{APP_VERSION} {APP_CHANNEL}"  # "0.0.2 Alpha"
USER_AGENT = f"emberleaf-launcher/{APP_VERSION}"

# Where new versions come from (GitHub releases, made by tools/release.ps1) and who publishes them
GITHUB_REPO = "SenLxxa/Emberleaf"
GITHUB_URL = f"https://github.com/{GITHUB_REPO}"
OWNER_NAME = "SenLxca"  # Minecraft name of the owner (gets the founder badge)

# The friends server everyone uses unless they enter another one in the settings
LAGERFEUER_URL = "https://lagerfeuer.emberleaf-server.workers.dev"
