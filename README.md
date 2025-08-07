# Data Tables

A lightweight mod for Fabric which adds a new data pack resource known as **data tables**.

Tags like `#minecraft:anvil` are great for adding behavior to entire groups of game elements, but what if you want to associate **different values** with each element?

This is where data tables come in: allowing you to define key-value pairs in JSON to map identifiers to different integer values. These tables can be queried as a mod developer or data pack creator.

## Example Data Table

As an example, we can make a data table that determines how "shiny" a certain block is.

```hjson
// my_mod_namespace/data_tables/shiny_blocks.json
{
  "type": "block",
  "default": 0,
  "entries": {
    "#my_data_pack:gem_blocks": 3,
    "minecraft:emerald_block": 5,
    "minecraft:diamond_block": 5,
    "minecraft:diamond_ore": 4
  }
}
```

This defines all ores under Fabric's common tag to a shiny value of 3, while Block of Emerald and
Block of Diamond are assigned to a value of 5.

## JSON Format

### `entries`

Defines a dictionary of key-value pairs where the key is an identifier (which is treated as a tag if prefixed by `#`) and the value is an integer.

Identifiers do not have to be valid block, item, or entity type IDs regardless of the `type` field—the parser will never throw an error due to this.

If there are conflicts, the **individual identifier** (e.g. `minecraft:emerald_block`) will always take precedence over the **tag identifier** (e.g. `#my_data_pack:gem_blocks`). This is an easy way to create exceptions to specific tags: even if `minecraft:emerald_block` is part of `#my_data_pack:gem_blocks` which is assigned a value of 3 in the above example, since it is specified individually it will return a value of 5 instead.

If the item is contained in multiple tags and the tags are associated with different values, the behavior is currently undefined: it will arbitrarily select one to use, and cannot return both.

* In the future, we may look at providing a way to resolve this more intentionally—for example, always taking the highest or lowest value. However, this functionality is not available yet.

### `default`

Defines the default value if a queried identifier does not match any item in the list. If omitted, defaults to `0`.

### `type`

The `type` field determines how the tags specified in `entries` are resolved. It can be set to `entity`, `item`, or `block` to resolve tags using entity type, item, or block tags respectively. It defaults to `misc` which does not resolve any tags; therefore, any data table with the `misc` type does not support tags.

This field does not in any way enforce how this data table can be queried. A `block` type data table can still be queried using an item identifier, which can be useful if you only want to define one data table for both blocks and items.

### `parents`

The `parents` field can be used to include one or more parent data tables which will be merged into this data table. This is useful when trying to extend a data table or merge multiple together.

## Setup

The server running the data pack must have this mod installed to be able to parse data tables. Clients do not need this mod installed usually, but it can be installed on client-side which will make data tables sync with the client. This can be useful if you have client-side code that relies on data tables.

### Reloading

Like other data pack resources, data tables update every time `/reload` is called.

### Overriding Data Tables

Data tables can also be overridden using data packs. Currently, appending existing data tables using data packs is not supported. This functionality may be added later.

## For Data Pack Creators

### Creating Data Tables

As a data pack creator, you can define data tables in JSON and add them to your data pack. Data tables are placed at the path `data/<namespace>/data_tables`, and can be further nested in folders.



### Querying Data Tables

Data tables can be queried via the `/table` command. For example, to query the block directly beneath you, you can run `/table get my_mod_namespace:shiny_blocks block ~ ~-1 ~`. You can store the resulting value with `/execute store` to use in other parts of the data pack.

## For Mod Developers

You can add this mod as a dependency in your Fabric mod project, which allows you to use the Data Table API.

*Example code pending*

### Creating Data Tables

Like data pack creators, you can define data tables manually in your mod's data folder to be included with your mod's data pack. However, you also have the option to use **data generation** to generate the data table using code.

To do this, create a class that extends `DataTableEntryProvider` and implement the `accept` method. The consumer requires an identifier (the namespace and name of the data table) as well as a `DataTableEntry`, which has a Builder class that can be accessed with `DataTableEntry.builder()` which allows for easy creation of data tables in code. Set the type and default value with `.type()` and `.defaultValue()` respectively, set parents with `.parents()` or `.parent()`, and add entries with `.entry()` or tags with `.tag()`. Finally, call `build()` to construct the DataTableEntry object.

Then in your `onInitializeDataGenerator` method in your main data generation entrypoint, make sure to call `pack.addProvider()` and add your data table generator class.

*Example code pending*

### Querying Data Tables

If you have the identifier for your data table such as `Identifier dataTableId = Identifier.of("my_mod_namespace", "my_data_table")`, you can retrieve the corresponding data table with `DataTables.get(dataTableId)`, which returns the Data Table object or `null` if the data table does not exist. Alternatively, you can use `DataTables.getOptional(dataTableId)` to get an Optional value instead.

You can use `DataTables.contains(dataTableId)` to see if a data table exists with that identifier, or `DataTables.getDataTableIds()` to get a list of all available data tables.

Once you have a `DataTable` object, you can call its `query` method to give it an identifier, item, block, or entity type. It will return the integer value associated with that identifier (or the default value if the identifier was not specified in the data table).
