# Clients without Battle.net

WeHavePhones can play a World of Warcraft client that you import yourself and
that starts without Battle.net, for example a 3.3.5a client for a server you
choose with its realmlist. Play starts the client's own executable directly:
there is no Battle.net sign-in, update check or install.

Only the official ARM64 Battle.net clients have been tested on a phone. A
32-bit x86 client runs through the same Wine/FEX runtime as Battle.net
(WOW64 emulation, Direct3D 9 through DXVK); how well it runs is untested.

## 1. Set up without Battle.net

On the first-run screen, tap **Install container** as usual. When the
container is ready, tap **Skip: I'll import my own client** instead of
**Install Battle.net**. The app opens Setup. You can still add a container
with Battle.net later with Setup's **Add New**.

An existing installation keeps its Battle.net games; imported clients appear
next to them.

## 2. Copy the client to the phone

Copy the client folder to the phone's internal-storage **Download** folder.
Use the folder that has the game executable (`Wow.exe`, `WoW.exe`,
`Wow-64.exe`, ...) directly inside it, not a folder above it:

```text
/storage/emulated/0/Download/WoW 3.3.5a/Wow.exe
/storage/emulated/0/Download/WoW 3.3.5a/Data/...
```

## 3. Import it

Open **Setup → Import your own client**. The list shows every folder in
Download with a Wow*.exe at its top level. Pick one. The app copies it, with
the same progress, notification and cancellation as **Import WoW files**, to

```text
C:\WoW Clients\<folder name>
```

and selects it on Play. The folder name keeps letters, digits, spaces and
`. _ ( ) -` because it goes into a batch file; a name without any of them
becomes `Client`. The name shown on Play is the original folder name. Your
files in Download are not changed; delete them yourself once the client
works.

## 4. Set the realmlist

On **Play**, tap the game title and pick the client. The second button now
reads **Realmlist**. Enter the server address, such as `logon.example.com`,
an IP address, or `host:port`. `set realmlist logon.example.com` is accepted
too. The app writes it where every client version looks:

- `realmlist.wtf` in the client folder (1.x, 2.x);
- `Data\<locale>\realmlist.wtf` for each locale folder, such as `zhCN` or
  `enUS` (3.x);
- `SET realmList "..."` in `WTF\Config.wtf` (4.x and later).

Missing files are created; other lines, such as `set patchlist`, are kept.
Play shows the server under the client's name.

## 5. Play

**Play** applies the runtime, writes the display settings and starts the
client:

- `WTF\Config.wtf` gets `gxWindow "1"`, `gxMaximize "1"` and `gxResolution`
  set to **Settings → Game resolution**, so the game fills the session
  screen without a mode switch. Other settings are kept.
- `C:\wow.bat` changes to the client folder and runs its executable. The
  **AAWoW_Retail** shortcut in Containers starts the same file.

The session returns to the menu when the game closes, as it does for
Battle.net games. If a session is already running, Play brings it back
instead of starting a second one.

## The marker file

Each imported client has `.wehavephones-client`, a UTF-8 properties file,
in its folder:

| Key | Meaning |
| --- | --- |
| `name` | Name shown on Play |
| `version` | Client build read from the executable at import, e.g. `3.3.5.12340` |
| `source` | Folder it was imported from |
| `exe` | Optional: executable to start instead of the detected Wow*.exe |
| `args` | Optional: extra command-line arguments (letters, digits, `. _ : / = \ -` and spaces) |

A folder you put under `C:\WoW Clients` yourself (Containers → ⋮ → Run, then
the container's file manager) also appears on Play when it has a Wow*.exe at
its top level; the marker is optional.

## What stays Battle.net only

For imported clients the app does not install ConsolePort or the pre-game
controller bridge (current ConsolePort raises Lua errors in older clients),
does not list them in the add-on manager, and does not write the retail
first-boot settings (portal, region, Direct3D 12). Manage their
`Interface\AddOns` folder yourself.

To remove a client, delete its folder under `C:\WoW Clients` in the
container's file manager.
