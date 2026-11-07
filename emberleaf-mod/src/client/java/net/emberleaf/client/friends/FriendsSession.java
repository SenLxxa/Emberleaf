package net.emberleaf.client.friends;

/** Login at the friends server; shared with the launcher through friends-session.json. */
record FriendsSession(String server, String token, String uuid, long expires) {}
