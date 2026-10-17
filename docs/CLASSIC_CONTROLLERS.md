# Controllers in imported clients

World of Warcraft reads a gamepad itself only from 9.0 on, and in the Classic
re-releases. A client from 1.x to 8.x, imported without Battle.net (see
[Clients without Battle.net](CUSTOM_CLIENTS.md)), cannot. For those clients
WeHavePhones plays the controller, and the on-screen gamepad, as keyboard and
mouse. The keys match the controller add-on the client has, so the add-on
works without remapping:

| Client | Controller add-on | Layout picked by Automatic |
| --- | --- | --- |
| 1.12.x | [ConsoleExperienceClassic](https://github.com/pepordev/ConsoleExperienceClassic) | ConsoleExperienceClassic |
| 1.12.x | [ShaguController](https://github.com/shagu/ShaguController) | Steam Deck keys |
| 3.3.5a | [ConsolePortLK](https://github.com/leoaviana/ConsolePortLK) | ConsolePortLK |
| 1.x to 8.x | none | Standard keys |
| 9.0 and later; 1.13+, 2.5+, 3.4+, 4.4+, 5.5+ | ConsolePort | none: the game gets an Xbox controller |

When the game starts, Automatic reads the client's build and its
`Interface\AddOns`: `ConsoleExperienceClassic`, then a `ConsolePort` whose TOC
says `## Interface: 3xxxx` (ConsolePortLK), then `ShaguController`. A folder
renamed `Name.disabled` does not count. An add-on switched off at character
select still does; pick the layout yourself then.

**Settings → Controller in imported clients** fixes a layout, or turns the
keys off (**Xbox controller**). A change applies within a second, also in a
running game.

## Keys

| Control | Standard keys | Steam Deck keys | ConsoleExperienceClassic | ConsolePortLK |
| --- | --- | --- | --- | --- |
| A | Space (jump) | Space | 1 | F11 |
| B | 3 | E | 4 | F10 |
| X | 1 | R | 2 | F12 |
| Y | 2 | F | 3 | F9 |
| D-pad up | 4 | Up arrow | 7 | F1 |
| D-pad right | 5 | Right arrow | 8 | F2 |
| D-pad down | 6 | Down arrow | 5 | F3 |
| D-pad left | 7 | Left arrow | 6 | F4 |
| LB | Tab (nearest enemy) | Wheel down | 0 | Shift |
| RB | 8 | Wheel up | 9 | F7 |
| LT | Right mouse button | Right mouse button | Shift (hold) | Ctrl (hold) |
| RT | Left mouse button | Left mouse button | Ctrl (hold) | F8 |
| Select / View | M (map) | Tab | M (map) | F5 |
| Start / Menu | Esc | Esc | Shift+Esc (radial menu) | F6 |
| L3 (left stick click) | Num Lock (autorun) | Shift | Left click | Left click |
| R3 (right stick click) | Left click | Left click | Right click | Right click |
| Left stick | W A S D | W A S D | W A S D | W A S D |
| Right stick | Pointer | Pointer | Camera | Pointer |

- **Standard keys** need no add-on: jump, the action bar keys 1 to 8, the
  nearest enemy, the map, autorun and the mouse on the triggers. Change what
  the keys do in WoW's own Key Bindings.
- **Steam Deck keys** are Valve's "Keyboard (WASD) and Mouse" template, which
  ShaguController binds in game.
- **ConsoleExperienceClassic** follows the add-on's bindings: 1 to 0, Shift on
  LT and Ctrl on RT for its second to fourth pages, Shift+Esc for its radial
  menu.
- **ConsolePortLK** uses the default keys of WoWpadX, the mapper it was made
  for.

## Sticks and triggers

- **Left stick:** W, A, S or D goes down past half tilt; a diagonal holds two
  keys. As with a keyboard, A and D turn the character, and strafe while the
  right mouse button is held.
- **Right stick, pointer:** it moves the mouse pointer, slowly near the center
  and quickly at full tilt. In Standard and Steam Deck keys, hold **LT** (the
  right mouse button) while you push it to steer the camera and the character,
  as with a mouse; tap LT on a character or object to use it. ConsolePortLK
  turns the camera itself.
- **Right stick, camera (ConsoleExperienceClassic):** the app puts the
  pointer on the add-on's crosshair and holds the right mouse button while the
  stick is pushed. When you let go, the pointer is back on the crosshair:
  **R3** (right click) talks to, loots or attacks what the crosshair is on,
  **L3** (left click) targets it. Menus and bags use the add-on's own cursor
  on the D-pad.
- **Triggers** count as pressed past about half travel.
- The pointer speed follows the cursor speed of the app's Input controls.

Keys and buttons are let go when the app goes to the background, the
controller disconnects or the game closes.

## Add-ons

Add an add-on to an imported client in the app's **Add-ons** screen: pick the
client at the top, then **Import ZIP**. A GitHub "Download ZIP" works as it
is: the app installs a `Name-main` or `Name-1.2.3` folder under the name of its
TOC, and each add-on of a repository that holds several. Imported clients only
take add-ons from Import; search and updates are for Battle.net games. You can
also copy add-ons into the client's `Interface\AddOns` before you import it.

### ConsoleExperienceClassic (1.12.1)

A console interface for vanilla and Turtle WoW: action bars laid out as the
controller, a radial menu, a cursor for menus and bags on the D-pad, a healer
mode for party frames and an on-screen keyboard for chat. It has a Simplified
Chinese translation. Type `/ce` in game for its settings. Its optional
Interact.dll (for Turtle WoW) is a client patch the app does not load.

### ShaguController (1.12.1)

A lighter set of interface changes by Shagu, the author of pfUI. It sets its
key bindings at login (A jumps, the face buttons and D-pad use the action
buttons, LB and RB pick targets, Tab opens the map) and restores your own at
logout. MIT licensed.

### ConsolePortLK (3.3.5a)

A backport of ConsolePort 1.9 to 3.3.5a. It reads keys that a mapper sends,
which the app now does with WoWpadX's defaults; do not also run WoWpadX in
the container. At the first login it asks for your controller type and to
calibrate: press each button it shows three times. `/cp recalibrate` repeats
that, `/cp config` opens its settings. Use its release ZIP; the repository is
a development tree.

### 2.4.3 and other versions

No controller add-on for 2.4.3 is known (ConsolePortLK's author did not port
it, for lack of the secure functions it needs). Standard keys apply; bind
what you need in WoW's Key Bindings.

## On-screen gamepad

While no controller is connected, the session shows an on-screen gamepad
(**Settings → Disable virtual gamepad** hides it). In an imported client it
sends the same keys as a controller; its right pad moves the pointer or turns
the camera. Touches outside its buttons still work as the touchpad.

## Your own mapping

- **Input controls:** a profile can map a controller's buttons, sticks and
  triggers to keys and the mouse (the app's Input controls settings →
  profile → External controllers). While such a profile is shown, it takes
  all input of that controller and the layouts above are off for it.
- **Xbox controller:** with the keys off, the game sees an Xbox controller.
  A mapper running inside the container, such as WoWpadX, could read it; that
  setup is untested.

## Before you enter the world

Add-ons only load in the world, so at login and character select a controller
sends its keys to the game's menus. Move the pointer with the right stick and
click with RT, L3 or R3 depending on the layout, or tap the screen. The side
menu's keyboard types your account and sends Enter.
