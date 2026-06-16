# MDVSocial 1.1.6

Plugin social inicial para MDVCRAFT.

## Que trae esta v1.1

- `/social`: abre el menu modular principal.
- Carpeta `plugins/MDVSocial/Menus/` para crear menus por YAML.
- Items que pueden abrir otros menus.
- Items que ejecutan comandos como jugador.
- Items para volver atras.
- Items para cerrar menu.
- Items para pagina anterior/siguiente.
- Menus con varias paginas usando `pages:`.
- `/titulos` o `/titulo`: menu de titulos y rangos.
- Sistema de titulos cosmeticos.
- Tienda de titulos con Vault/Economy.
- Titulos comprables con dinero de juego.
- Titulos desbloqueables por comando.
- Titulos desbloqueables por permiso de LuckPerms.
- Placeholders para chat/tab.

## Requisitos recomendados

- Paper/Purpur 1.21.6
- Java 21
- Vault + plugin de economia para tienda
- PlaceholderAPI para mostrar titulos en chat/tab
- LuckPerms para permisos de titulos/rangos

## Menus modulares

Al iniciar el plugin crea:

```txt
plugins/MDVSocial/Menus/main.yml
plugins/MDVSocial/Menus/clan.yml
plugins/MDVSocial/Menus/ayuda.yml
```

El menu que abre `/social` se define en `config.yml`:

```yaml
settings:
  start-menu: main
```

Cada archivo dentro de `Menus/` es un menu. El nombre del archivo es el ID del menu.

Ejemplo:

```txt
Menus/main.yml -> menu id: main
Menus/clan.yml -> menu id: clan
Menus/crear_clan.yml -> menu id: crear_clan
```

## Formato basico de un menu

```yaml
title: '&8MDVSocial'
size: 27
items:
  clan:
    slot: 13
    material: SHIELD
    name: '&aClan'
    lore:
      - '&7Abre el menu de clan.'
      - '&eClick para abrir.'
    action: OPEN_MENU
    target-menu: clan

  cerrar:
    slot: 26
    material: BARRIER
    name: '&cCerrar'
    action: CLOSE
```

## Acciones disponibles

```txt
OPEN_MENU      -> abre otro menu de la carpeta Menus
COMMAND_PLAYER -> ejecuta comandos como jugador
BACK           -> vuelve al menu anterior
CLOSE          -> cierra el inventario
PREVIOUS_PAGE  -> pagina anterior del mismo menu
NEXT_PAGE      -> pagina siguiente del mismo menu
OPEN_TITLES    -> abre el menu interno de titulos de MDVSocial
```

## Ejecutar comandos como jugador

```yaml
amigos:
  slot: 10
  material: PLAYER_HEAD
  head-owner: '{player}'
  name: '&bAmigos'
  lore:
    - '&7Ejecuta /friends como jugador.'
  action: COMMAND_PLAYER
  close-on-click: true
  commands:
    - 'friends'
```

Puedes usar `{player}` en comandos, nombres y lore.

## Volver atras

```yaml
volver:
  slot: 18
  material: ARROW
  name: '&eVolver'
  action: BACK
```

El plugin recuerda desde que menu llego el jugador. Si no hay menu anterior, vuelve al menu principal.

## Menus con paginas

```yaml
title: '&8Ejemplo pagina {page}/{max_page}'
size: 27
pages:
  1:
    items:
      pagina2:
        slot: 26
        material: SPECTRAL_ARROW
        name: '&ePagina siguiente'
        action: NEXT_PAGE

  2:
    items:
      pagina1:
        slot: 18
        material: ARROW
        name: '&ePagina anterior'
        action: PREVIOUS_PAGE
      cerrar:
        slot: 26
        material: BARRIER
        name: '&cCerrar'
        action: CLOSE
```

## Comandos jugador

```txt
/social
/titulos
/titulo poner <id>
/titulo quitar
```

## Comandos admin

```txt
/mdvsocial reload
/mdvsocial title give <jugador> <titulo>
/mdvsocial title remove <jugador> <titulo>
/mdvsocial title set <jugador> <titulo>
/mdvsocial title clear <jugador>
/mdvsocial title give-radius <radio> <titulo>
/mdvsocial title give-near <world> <x> <y> <z> <radio> <titulo>
```

## Placeholders

```txt
%mdvsocial_title%
%mdvsocial_title_colored%
%mdvsocial_title_prefix%
%mdvsocial_active_title%
%mdvsocial_unlocked_titles%
```

Para LPC/chat puedes usar algo como:

```txt
%mdvsocial_title_prefix%{prefix}{name} » {message}
```

## GitHub Actions

El proyecto trae `.github/workflows/maven.yml`.

Sube el proyecto a GitHub y compila desde Actions.

## 1.1.6 - Cabezas custom en menus

Esta version agrega texturas base64 para cualquier item de menu que use `material: PLAYER_HEAD`.

Funciona en:

```yaml
items:
  back:
    material: PLAYER_HEAD
    custom-head-texture: 'BASE64'
    name: '&6Volver'
```

Y tambien en menus de `plugins/MDVSocial/Menus/`:

```yaml
volver:
  slot: 18
  material: PLAYER_HEAD
  texture: 'BASE64'
  name: '&6Volver'
  action: BACK
```

Aliases aceptados:

```yaml
custom-head-texture: 'BASE64'
texture: 'BASE64'
head-texture: 'BASE64'
skull-texture: 'BASE64'
texture-base64: 'BASE64'
```

Si una cabeza tiene textura, se usa la textura. Si no tiene textura pero tiene `head-owner`, se usa la cabeza del jugador indicado.

## 1.1.6 - PlaceholderAPI en menus

Los menus modulares parsean PlaceholderAPI en `name`, `lore` y comandos `COMMAND_PLAYER`.

Atajos disponibles:

- `{player}`
- `{level}` -> `%mmocore_level%`
- `{exp}` / `{experience}` -> `%mmocore_experience%`
- `{next_level}` -> `%mmocore_next_level%`
- `{percent}` -> `%mmocore_level_percent%`
- `{progress}` -> barra visual de progreso
- `{class}` -> `%mmocore_class%`
- `{class_id}` -> `%mmocore_class_id%`
- `{attribute_points}` -> `%mmocore_attribute_points%`

Tambien puedes usar placeholders PAPI directamente, por ejemplo `%mmocore_level%`.

## 1.1.6 - Titulo obligatorio / Forastero visible

Configuracion recomendada:

```yaml
settings:
  mandatory-title: true
  allow-clear-title: false
  default-title: forastero
  default-unlocked-titles:
    - aventurero
  hide-default-title-in-menus: true
  hidden-titles:
    - forastero
```

- `forastero` funciona como titulo por defecto visible en chat.
- `forastero` queda oculto de menus y no es equipable por jugadores si usa `player-equippable: false`.
- `aventurero` queda desbloqueado para todos y puede equiparse despues.
- Jugadores nuevos sin titulo activo usan `settings.default-title: forastero` sin necesidad de guardarlo en `player-data.yml`.
- Si `allow-clear-title` es false, el jugador no puede quedarse sin titulo con `/titulo quitar`.

Para equipar Aventurero al elegir raza desde MMOCore:

```yaml
triggers:
  class-chosen:
  - 'command{format="mdvsocial title set %player_name% aventurero"}'
```
