# Vaporous: a vapor speedster for Fabric 1.21.1

This speedster gives off wisps of smoke instead of lightning. The faster he runs, the thinner his body gets. A lighter body runs faster, jumps higher, rides the wind and runs up walls. He also has an ability that slows the whole world down while he keeps moving at full speed.

## Requirements
- Minecraft **1.21.1**
- Fabric Loader 0.15.11 or newer
- Fabric API (any 1.21.1 build)
- To build it yourself: Java 21 (JDK)

## Building the .jar
1. Install a **Java 21 JDK**. Temurin from adoptium.net works.
2. Open a terminal in this folder and run:
   - Windows: `gradlew.bat build`
   - macOS/Linux: `./gradlew build`

   The first build downloads Gradle, Minecraft and the Fabric toolchain, so it takes a few minutes.
3. The finished mod is `build/libs/vaporous-0.1.0.jar`. Ignore the `-sources` jar.
4. Copy it into `.minecraft/mods` next to Fabric API.

To test quickly without installing, run `./gradlew runClient`.

You can also open the folder in IntelliJ IDEA and import it as a Gradle project.

## Getting the powers
- **Craft:** a *Phial of Living Mist*, shapeless: glass bottle + breeze rod + phantom membrane + sugar + feather. Drink it.
- **Or use a command (op):** `/vaporous grant [players]`. Undo with `/vaporous revoke [players]`.

## Controls (rebind under Controls → Key Binds → Vaporous)
| Key | Ability |
|---|---|
| **V** | Toggle **Vapor Form**, the speed power |
| **Z** | Toggle **Time Dilation** |
| **R** (hold, then release)* | **Pressure Jet**: positive-pressure blast. Hold longer to compress it harder. Sneak and look down while firing to jet yourself into the air. |
| **G** (hold, then release)* | **Vacuum Well**: negative-pressure pocket where you're looking. It pulls mobs and items in, takes the air out of their lungs, then collapses with a thunderclap. Sneak to open it right in front of you. |
| Sprint | Builds momentum. Your body only thins out properly at a full sprint. |
| Sneak (on the ground) | Condense back down on purpose |
| Hold Jump (in the air) | Ride air currents |
| Push into a wall | Run up it. Sneak to cling in place. |
| At density 20% or lower: hold forward and look up | **Slipstream flight**: you fly wherever you're looking. Look down at the ground to land, let go of forward to coast on your momentum, or sneak to drop. |

\* **R and G need speed or slowed time.** In normal time you can only compress air while you're already moving fast (density 50% or lower). With Time Dilation on, you can use them even standing still.

## Speedometer
A dial in the bottom-right shows your speed (mph by default, or km/h or blocks/s in the config). It uses a log scale, so walking, sprinting and supersonic speeds all fit.

You always run at the same *real* speed, but with time slowed the world sees you cover ground N times faster. The speedometer shows that **perceived** speed, which is your real speed × the time factor. A 130 mph sprint in 1/20 time reads about 2,600 mph, or Mach 3.4.

While time is slowed, the dial gets a cyan rim and shows "PERCEIVED ×N", and the needle can swing into the cyan zone past 150 mph that you can't reach in normal time. A MACH readout appears near the sound barrier.

## What each ability does
**Vapor trail.** Thousands of translucent wisps made from vanilla's soft dust/smoke sprites. Each one billows outward, curls, rises and fades. Wisps are seeded along the whole path you covered each tick, so the trail stays one solid ribbon even at 60+ blocks/second. Colour, opacity and density are in the config.

**Density goes down as velocity goes up.** The mod measures your real ground speed and turns it into a 0–100% vapor level, shown as the *Density* gauge on the HUD. Vapor raises movement speed, jump strength and safe-fall distance, and lowers gravity and fall damage. Since more speed means more vapor and more vapor means more speed, a full sprint keeps accelerating, from about 11 to about 60 blocks/second, over a few seconds.

**Gets stronger as he thins:**
- Slipstream flight at 80% vapor or more (density 20% or lower). You fly with your momentum along your view: speed builds and bleeds off gradually, and turns bank smoothly instead of snapping. Airspeed keeps your body thin, so you can stay up as long as you keep pushing forward. Flying, wall-running and riding currents don't build up fall damage.
- Wall-running above 25% vapor
- Riding air currents above 35% vapor. Holding jump caps your fall speed and keeps your momentum. The wind slowly shifts direction, grows stronger with altitude and storms, and pushes you along. Fire, lava, magma and lit campfires below you create **thermals** that lift you.

**Momentum strikes.** Melee damage, knockback and attack speed scale with vapor. Your hits also launch targets in the direction you're running. In vapor form, hits ignore the target's invulnerability frames, so you can land flurries.

**Time perception.** With Time Dilation on, the world runs 8× slower. The faster you go, the slower it gets, up to 20×.

### How the time slowdown works
Minecraft 1.21 has a built-in `/tick freeze` system. Vaporous hooks into it so the world only advances on 1 tick out of every N. On the other ticks, the game treats everything except players as frozen: mobs, projectiles, items, falling blocks, water and lava flow, crop growth, furnaces, redstone, weather and the day cycle.

Players keep ticking every tick, so your walking, gravity, jumping, mining, block placing, eating and attacking run at normal speed while the world crawls.

Slowed things don't stutter. On your screen, each slowed tick is stretched over N real ticks so mobs and arrows move smoothly. World particles slow down too, but your own vapor trail doesn't. Sounds drop in pitch, and a faint cold haze covers the screen. Both can be turned off in the config.

Your pressure attacks hit right away, but the knockback plays out in slow motion. The Vacuum Well also slows down with the world.

## Config
`config/vaporous.json` is created on first launch, and every value has a comment in `VaporConfig.java`. Run `/vaporous reload` to apply server-side values. Restart the game for client-side values: movement, trail and HUD.

## Multiplayer notes
- Install the mod on **both** server and clients.
- Set `allow-flight=true` in `server.properties`. Otherwise riding updrafts can trip vanilla's "flying is not enabled" kick. Singleplayer already allows it.
- Time Dilation is shared across the whole server. It slows every non-player entity for everyone, and other players keep moving at normal speed.
- Very high speeds on a busy server can cause rubber-banding and chunk-loading lag. Turn `maxSpeedBonus` down if that happens.

## Code map
- `time/TimeDilation` + `mixin/TickManagerMixin`: selective slow motion
- `mixin/client/WorldRendererMixin`, `ParticleManagerMixin`: smooth slow-mo rendering
- `power/SpeedsterManager`: who's powered, toggles, syncing, time factor, momentum hits
- `power/VaporAttributes`: vapor level → attribute changes
- `power/PressureAbilities`: Pressure Jet and Vacuum Well
- `client/VaporClientController`, `VaporMovement`, `AirCurrents`: speed measurement, wall-running, air-current riding
- `client/VaporTrail`, `VaporParticle`: the smoke trail
- `client/VaporHud`, `VaporKeys`: HUD and key bindings

## Adding more abilities
Add a constant in `VaporPayloads.Ability`, a key in `VaporKeys`, and a `case` in `SpeedsterManager.handleAbility`.
