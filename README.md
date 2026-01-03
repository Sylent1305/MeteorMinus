# MeteorMinus

A utility addon for Meteor Client.

## Features

### Meteorminus Category
Adds a dedicated category to the Meteor Client GUI.

### Auto Trash
Automates the process of clearing inventory using server-side trash commands.
* **Smart Scan:** Checks inventory for matching items before executing the command to reduce server traffic.
* **Filter Modes:** Supports Whitelist (trash only selected) and Blacklist (trash everything except selected).
* **Adjustable Speed:** Custom tick delays for both the command execution and individual item dumping.

### Auto Sell
Automatically executes selling routines based on a configurable tick timer for economy servers.

---

## Installation

1. Ensure Meteor Client is installed.
2. Place the addon .jar file into the `.minecraft/meteor-client/addons` folder.
3. Restart Minecraft.

---

## Requirements

* Minecraft (Fabric)
* Meteor Client
* Java Runtime Environment (JDK) 21

### Building from source
```bash
git clone [https://github.com/YourUsername/MeteorMinus.git](https://github.com/YourUsername/MeteorMinus.git)
cd MeteorMinus
./gradlew build
