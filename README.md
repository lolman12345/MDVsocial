# MDVSocial 1.2.7

Plugin social modular para MDVCRAFT.

## Incluye

- Menús modulares en `plugins/MDVSocial/Menus/`.
- Títulos cosméticos y rangos visuales.
- Placeholders de título para PlaceholderAPI.
- Placeholders inteligentes de party para scoreboard.
- Puentes para GUIs externas.
- Menú configurable de opciones de amigo desde la lista de MMOCore.
- Sistema base de cartas/correo interno.
- Responder cartas directamente desde la lectura.
- Las cartas oficiales de MDVCRAFT no pueden bloquearse.

## Placeholders de título

- `%mdvsocial_title%`
- `%mdvsocial_title_colored%`
- `%mdvsocial_title_prefix%`
- `%mdvsocial_active_title%`
- `%mdvsocial_unlocked_titles%`

## Placeholders de party para scoreboard

Estos placeholders usan MMOCore si está activo. Si el jugador no está en party, las líneas visuales devuelven vacío para no mostrar la sección de grupo.

- `%mdvsocial_party_header%` → `Grupo: actual/max`
- `%mdvsocial_party_member_1%`
- `%mdvsocial_party_member_2%`
- `%mdvsocial_party_member_3%`
- `%mdvsocial_party_member_4%`
- `%mdvsocial_party_member_5%`
- `%mdvsocial_party_count%`
- `%mdvsocial_party_max%`
- `%mdvsocial_party_members%`
- `%mdvsocial_party_in_group%`
- `%mdvsocial_party_spacer%`

El máximo se lee desde:

```yaml
social-friend-options:
  party:
    max-members: 5
```

## Sistema de cartas

Comandos:

- `/correo` abre el menú de correo.
- `/carta buzon` abre el buzón.
- `/carta enviar <jugador> <mensaje>` envía una carta.
- `/carta bloquear <jugador>` bloquea cartas de ese jugador.
- `/carta desbloquear <jugador>` desbloquea cartas de ese jugador.
- `/carta bloqueados` muestra bloqueados.
- `/carta cancelar` cancela una escritura por chat.

Acciones para menús modulares:

- `OPEN_MAILBOX`
- `START_MAIL_SEND`
- `START_MAIL_SEND_TARGET`
- `START_MAIL_BLOCK`
- `START_MAIL_UNBLOCK`
- `REPLY_MAIL`
- `INVITE_PARTY_TARGET`

Datos:

- `player-data.yml` guarda títulos.
- `mail-data.yml` guarda cartas y bloqueos.

Las cartas expiran automáticamente según `mail.expire-after-days`.


## MDVSocial 1.2.5

Agrega sincronizacion automatica para AnimatedScoreboard cuando el jugador esta en party de MMOCore.

- Al entrar al servidor: `animatedscoreboard.party = false` por seguridad.
- Si el jugador esta en party: `animatedscoreboard.party = true`.
- Si el jugador sale de party: `animatedscoreboard.party = false`.
- La sincronizacion se revisa cada `scoreboard-party-permission.sync-interval-ticks` ticks.

Bloque de config recomendado:

```yaml
scoreboard-party-permission:
  enabled: true
  permission: animatedscoreboard.party
  reset-on-join: true
  sync-interval-ticks: 20
  debug: false
```

Config recomendada en AnimatedScoreboard:

```yaml
worlds:
  global:
  - mdvcraftparty
  - defaultscoreboard

  world:
  - mdvcraftparty
  - defaultscoreboard

  world_the_end:
  - mdvcraftparty
  - defaultscoreboard

  world_nether:
  - mdvcraftparty
  - defaultscoreboard

permissions:
  mdvcraftparty: animatedscoreboard.party
```


## MDVSocial 1.2.7

Agrega integración directa con MDVClans como motor de clanes.

Nuevas acciones de menús modulares:

```yaml
action: OPEN_CONDITIONAL_MENU
condition-placeholder: '%mdvclans_is_in_clan%'
condition-equals: 'true'
true-menu: clan_con_clan
false-menu: clan_sin_clan
```

```yaml
action: MDVCLANS_OPEN
clans-menu: miembros
```

`MDVCLANS_OPEN` ejecuta internamente `/clan abrir <menu>`, así MDVSocial puede tener menús bonitos y MDVClans conserva las UIs dinámicas.


## Cambios 1.2.7

- `Menus/clan_con_clan.yml`: el item Tablero de información muestra placeholders de MDVClans y ejecuta `/clan tablero ver` al hacer click.
