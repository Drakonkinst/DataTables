# Data Tables

A lightweight mod for Fabric that adds a new data pack resource, **data_tables**.

Data pack tags are great for adding behavior to entire groups of game elements, but what if you
want to assign **different values** to each element? With vanilla data packs, the only way to
accomplish this is to make different tags for each discrete value. Now, you can write a **JSON file
** that maps identifiers to different values!

As an example, we can make a data table that determines how "shiny" a certain block is.

```json
{
  "type": "block",
  "default": 0,
  "entries": {
    "#c:ores": 3,
    "minecraft:emerald_block": 5,
    "minecraft:diamond_block": 5,
    "minecraft:diamond_ore": 4
  }
}
```

This defines all ores under Fabric's common tag to a shiny value of 3, while Block of Emerald and
Block of Diamond are assigned to a value of 5. Even though `minecraft:diamond_ore` is part
of `c:ores`, because it is listed individually this will override Diamond Ore to have a shiny value
of 4 instead.

Data tables can be placed at the path `data/<namespace>/data_tables`. Data tables allow
configuration of entries that map an identifier (for a block, entity, item, etc.) to an integer
value. It also supports **tags**, which allows developers (found at `data/<namespace>/tags`) to
specify entire groups of blocks easily. The `type` parameter determines how tags are processed.

Like other data pack resources, these update every time `/reload` is called. Data packs can also
append or override each other's data tables to change behavior or add blocks from another mod. For
example, a mod using one of these data tables to add functionality can be overridden by a data pack
to change the mod's behavior.

A new command, `/table` can be used to access data table values as a data pack developer.

This can be installed on server-side to enable this feature for data packs. In addition, it can be
installed on the client which will make data tables automatically sync on the client side every
time `/reload` is run.

This mod is very WIP, and more functionality will be added as needed and requested.