# JustBoxed 📦

**JustBoxed** is a 26.2 Paper Plugin implementing a Boxed-style game mode where each player or team evolves in a confined world whose border expands as achievements are unlocked.

---

## Features

- **Customized and isolated worlds**: Each box has its own world generated instantly by asynchronous duplication of an optimized global template (seed with all biomes and structures nearby).
- **Dynamix world border**: The playing area starts with a 1×1 block border and automatically expands by **+2 blocks** for each advancement unlocked by the team.
- **Team-based shared progress** :
  - Progress made by one member is automatically shared with all other connected members.
  - Automatic synchronization of unlocked offline progress upon reconnection with a summary in the chat.
- **Complete team management**: Invitation system, eviction, ownership transfer, renaming and secure deletion with confirmation.
- **Persistent & asynchronous storage** : Save data via SQLite without blocking the server's main loop.

---

## Prerequisites

- **Java**: Version 25 or higher
- **Server** : Paper 26.2+

---

## Installation

1. Download the `.jar` file of the latest version of the plugin.
2. Place the file in the `plugins/` folder of your Paper server.
3. Start or restart your server.
4. On first startup, the plugin automatically generates the world template in `world/dimensions/justboxed/box_template` and a database in the `plugin/JustBoxed` folder.

---

## Compilation

You can also compile the project youself to use it

```bash
# Clone the git repo
git clone https://github.com/hthug06/JustBoxed-Paper.git
cd JustBoxed-Paper

# Compile with Gradle
./gradlew build
```

## What else?
The world seed is `8500081009970950196`. This seed contains all biomes and structures in a 1000 block radius.

For a better experience, I recommend you to use the [blazes and caves](https://modrinth.com/datapack/blazeandcaves-advancements-pack) datapack (else, you literally can't finish the main game lol).

Also, go check [Yeah Jaron Video](https://www.youtube.com/watch?v=nH_DkXnD3ek) for a better understanding of the plugin.
