# Vaporous: vapor and plasma speedsters for Fabric 1.21.1

These speedsters give off wisps of smoke or glowing plasma instead of lightning. The faster they run, the thinner their bodies get. A lighter body runs faster, jumps higher, flies, rides the wind and runs up walls. They also have an ability that slows the whole world down while they keep moving at full speed.

## Requirements
- Minecraft **1.21.1**
- Fabric Loader 0.15.11 or newer
- Fabric API (any 1.21.1 build)

## Building the .jar
- **GitHub (no Java needed):** push this folder to a GitHub repository. The included workflow builds the jar automatically. Download it from the Actions tab under **vaporous-jar**.
- **On your PC:** install a Java 21 JDK, then double-click `BUILD-JAR-WINDOWS.bat`, or run `./build-jar-mac-linux.sh`.

Put `vaporous-0.1.0.jar` in `.minecraft/mods` with Fabric API.

## The two phials
| | Phial of Living Mist | Phial of Living Plasma |
|---|---|---|
| Recipe (shapeless) | glass bottle + breeze rod + phantom membrane + sugar + feather | Phial of Living Mist + nether star + blaze rod + glowstone dust + amethyst shard |
| Trail | thick, translucent grey-white vapor | the same dust wisps, self-lit and glowing: a white-hot flash that cools quickly through cyan to violet, then lingers as a long, flickering violet ribbon that throws sparks |
| Top sprint | about 60 blocks/s (≈130 mph) | about 140 blocks/s (≈310 mph) |
| Flight | about 60 blocks/s | about 120 blocks/s |
| Time dilation | 1/8 → 1/20 as you speed up | 1/24 → 1/60 as you speed up |
| Hunger | speed and time dilation drain hunger | normal hunger only: no extra cost for speed, time dilation or abilities |

Drinking the other phial switches you over. Ops can use `/vaporous grant [players] [vapor|plasma]`, `/vaporous revoke [players]` and `/vaporous reload`.

## Controls (rebind under Controls → Key Binds → Vaporous)
| Input | Ability |
|---|---|
| **V** | Toggle the speed form |
| **Z** | Toggle **Time Dilation** |
| **R** (hold, then release)* | **Pressure Jet**: positive-pressure blast. Hold longer to compress it harder. Sneak and look down while firing to jet yourself upward. |
| **Right-click a projectile** (slowed time only) | **Catch it out of the air.** Arrows, tridents, snowballs, eggs, pearls, potions and fireworks go into your inventory. Anything with no item form is plucked out of the air. |
| **Hold right-click with arrows in hand** | **Wind up and throw**, like a trident. A longer wind-up throws harder, and a full charge is a critical. Arrows in the off hand work if your main hand is empty. |
| Sprint | Builds momentum. Your body only thins out properly at a full sprint. |
| Sneak (on the ground) | Condense back down on purpose |
| Hold Jump (in the air) | Ride air currents |
| Push into a wall | Run up it. Sneak to cling in place. |
| At density 20% or lower: hold forward and look up | **Slipstream flight**: fly wherever you're looking. Look down at the ground to land, let go of forward to coast, or sneak to drop. |

\* **R needs speed or slowed time.** In normal time you can only compress air while you're already moving fast (density 50% or lower). With Time Dilation on, you can use it even standing still.

## Momentum
All of these use your speed **relative to the world**. In 1/20 time, the world sees you move 20× faster, so a punch that reads Mach 2 on the speedometer lands like a Mach 2 punch.
- **Kinetic strikes.** Hit something while moving forward and the hit adds damage that grows with the *square* of your speed, like kinetic energy. A full vapor sprint in normal time adds about 5. The same sprint in slowed time adds hundreds, so nothing ordinary survives it.
- **Flung, not nudged.** The target takes your momentum and sails off on a long, high arc in your direction of travel, trailing vapor (or plasma) behind it. Harder hits throw higher and farther. Heavy, knockback-resistant mobs move less.
- **Wall impact.** A flung creature that slams into a block hard enough takes impact damage, with debris and a thud. This also applies to anything blown back by the Pressure Jet.
- **Cradle chains.** A flung creature that crashes into another one hands the momentum on and stops short, like the balls of a Newton's cradle.
- **Projectiles.** Anything you shoot or throw while moving forward gains your speed. Arrows and tridents also get a kinetic damage bonus that grows with the square of the added speed. A fast projectile flings whatever it hits.
- **Hand-thrown arrows** are weak when thrown standing still, about 1 heart at full wind-up. Throwing while running, and especially in slowed time, is what makes them deadly.
- **Untouchable in slowed time.** Touching or hitting a mob while time is dilated makes you immune to mob damage for 1 second, refreshed as long as you stay in contact. Nothing could react that fast.
- In vapor or plasma form, your melee hits ignore the target's invulnerability frames, so flurries land.

## Speedometer
A dial in the bottom-right shows your speed in mph by default; the config can switch it to km/h or blocks/s. It's on a log scale, so walking, sprinting and hypersonic speeds all fit.

With time slowed, the world sees you cover ground N times faster. The speedometer shows that **perceived** speed: real speed × the time factor. A plasma sprint at 1/60 time reads about 18,000 mph, or Mach 24.

While time is slowed, the dial gets a coloured rim and shows "PERCEIVED ×N". The needle can only reach the coloured zone of the dial in slowed time.

## Movement
- **Density goes down as velocity goes up.** The *Density* gauge shows it. Thinner means faster, higher jumps, lower gravity and softer landings.
- **Step-ups scale with speed.** Starting at about 1.1 blocks, you flow up ledges up to about 2.6 blocks tall at full speed without jumping.
- **Step-downs.** Run off a ledge or down a slope and you stay glued to the ground instead of launching off it. This works for drops up to 0.6 blocks normally and up to 2.5 blocks at full speed. Jump to leave the ground on purpose.
- **Wall-running** above 25% vapor, **riding air currents** above 35%, and **Slipstream flight** at 80% or more. None of these build up fall damage.

## Item pickup
Speedsters pick up items instantly, even in slowed time. Normally a freshly dropped item can't be collected until a short timer runs out, and in slowed time that timer runs slow too. Speedsters skip it.

Pickup also covers the whole path you ran since the last tick, so you don't blow past items at speed. Items you drop on purpose keep their normal delay.

## How the time slowdown works
Minecraft 1.21 has a built-in `/tick freeze` system. Vaporous hooks into it so the world only advances on 1 tick out of every N, and everything except players skips the other ticks. That covers mobs, projectiles, items, falling blocks, water and lava flow, crops, furnaces, redstone, weather and the day cycle.

Players keep ticking normally, so their walking, gravity, jumping, mining, placing and attacking run at full speed. Slowed things are smoothly interpolated so they glide instead of stuttering. Your own trail keeps your speed, while sounds drop in pitch.

## Config
`config/vaporous.json` is created on first launch. Each phial has its own section, `vapor` and `plasma`, for speed, flight speed, time factors and hunger costs. Shared settings cover momentum, impact damage, catching, throwing, pickup, step heights and trail colours. Every value is commented in `VaporConfig.java`. Run `/vaporous reload` to apply server-side values; client-side values need a restart.

## Multiplayer notes
- Install the mod on **both** server and clients.
- Set `allow-flight=true` in `server.properties`. Otherwise flight and updrafts can trip vanilla's "flying is not enabled" kick.
- Time Dilation is shared across the whole server.
- Plasma speeds are very high. On a busy server, expect chunk-loading lag, and turn down `plasma.maxSpeedBonus` if needed.

## Code map
- `time/TimeDilation` + `mixin/TickManagerMixin`: selective slow motion
- `power/SpeedsterManager`: phial variant, toggles, syncing, time factor, motion tracking
- `power/MomentumCombat`: cradle strikes, kinetic impact, cradle chains, projectile momentum
- `power/ProjectileCatching` + `client/ProjectileHands`: catching and arrow throwing
- `power/FastPickup`: instant pickup along your path
- `power/PressureAbilities`: Pressure Jet
- `power/VaporAttributes`: vapor level → attribute changes, including step height
- `client/VaporMovement`: step-downs, flight, wall-running, air currents
- `client/VaporTrail`, `VaporParticle`: vapor and plasma trails
- `client/VaporHud`: density gauge, speedometer, charge bars
