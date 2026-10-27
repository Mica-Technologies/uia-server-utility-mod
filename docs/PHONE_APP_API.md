# SUM Phone App API

Another mod can add an app to the SUM phone. It gets a tile on the home screen and, once opened, the
phone's screen to draw in: the same area SUM's own Notes or Pay app has, inside the same bezel, status
bar and home button. Everything is in `com.micatechnologies.minecraft.sum.api.phone`, ships in the
`-api` jar, and is versioned by `PhoneApps.API_VERSION` (currently `1`).

## Registering

Client side only, from a client proxy's init. SUM is optional for most mods, and SUM versions before
this API have no `api.phone` package, so guard both:

```java
if (Loader.isModLoaded("sum")) {
    try {
        if (PhoneApps.isCompatible(1)) {
            PhoneApps.register(new MyApp());
        }
    } catch (NoClassDefFoundError e) {
        // An older SUM: no phone apps.
    }
}
```

Apps appear after SUM's own, in registration order. When there are more than eight, the home screen
pages, with arrows either side of the home button.

## Writing an app

Implement `PhoneApp`. Five methods are required:

| Method | What it does |
| --- | --- |
| `id()` | Stable unique id, namespaced: `"mymod:thing"` |
| `title()` | The label under the tile, localised; about 50 px fits |
| `iconColor()` | The icon square's ARGB colour |
| `iconGlyph()` | One or two characters on the icon |
| `draw(screen, mouseX, mouseY, partialTicks)` | Draw the app, every frame |

Optional, all with defaults: `isAvailable` (hide on desk phones, say), `onOpen`, `tick` (20 a
second), `mouseClicked`, `mouseScrolled`, `keyTyped` (never Escape, which closes the phone),
`showsBackButton` and `onBack` (return true if you went up a level yourself; false sends the phone
home), `onClose`.

Draw through the `PhoneScreen` you are handed: `left()`/`top()`/`right()`/`bottom()` give your area
(about 132 x 168 px; nothing is clipped for you), plus `fill`, `drawText`, `drawCenteredText`,
`textWidth`, `trimToWidth`, `lineHeight` and `goHome`. Use `PhonePalette`'s colours to match SUM's
apps. An app's own code can use any client class it likes; the API types themselves stay free of
client-only classes so the `-api` jar loads on a dedicated server.

An app that throws does not take the phone down: SUM catches it, and a failed draw shows
"This app ran into a problem." in its place.

## Opening the phone from another mod

`PhoneApps.openPhone()` opens the phone for the local player, as their phone item would; it returns
false on a dedicated server or before SUM's client setup has run. CSM's phone items use it.

## Getting data onto the phone

The phone is a client screen, so an app that shows server data needs its own way to fetch it: send a
request packet on your mod's channel when the app opens (and on a timer while it is open), and draw
from a client-side cache the reply fills. SUM's own apps work the same way (see
`phone/cloud/PhoneCloudFetchRequest`).
