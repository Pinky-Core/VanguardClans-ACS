# VanguardClans Multi-Storage System

## Overview

VanguardClans now supports multiple storage backends, allowing you to choose the most suitable option for your server setup.

## Supported Storage Types

### 1. YAML (Default)
- **File**: `plugins/VanguardClans/clans_data.yml`
- **Pros**: No database setup required, easy to backup, human-readable
- **Cons**: Slower for large datasets, not suitable for high-traffic servers
- **Best for**: Small servers, testing, development

### 2. H2 Database
- **File**: `plugins/VanguardClans/clans.h2.db`
- **Pros**: Fast, embedded database, no external setup required
- **Cons**: Single-file database, limited concurrent connections
- **Best for**: Medium-sized servers, when you want SQL features without external database

### 3. SQLite
- **File**: `plugins/VanguardClans/clans.db`
- **Pros**: Lightweight, reliable, widely supported
- **Cons**: Limited concurrent writes, not suitable for high-traffic servers
- **Best for**: Small to medium servers, when you need reliability

### 4. MySQL
- **External database required**
- **Pros**: High performance, supports multiple servers, robust
- **Cons**: Requires external database setup
- **Best for**: Large servers, multi-server networks

### 5. MariaDB
- **External database required**
- **Pros**: High performance, open-source, compatible with MySQL
- **Cons**: Requires external database setup
- **Best for**: Large servers, when you prefer MariaDB over MySQL

## Configuration

Edit `plugins/VanguardClans/config.yml`:

```yaml
storage:
  # Storage type: mariadb, mysql, h2, sqlite, yaml
  # YAML is recommended for small servers or when you don't have a database
  type: "yaml"

  # MariaDB configuration
  mariadb:
    host: "localhost"
    port: 3306
    database: "satipoclans"
    username: "root"
    password: "password"

  # MySQL configuration
  mysql:
    host: "localhost"
    port: 3306
    database: "satipoclans"
    username: "root"
    password: "password"

  # H2 configuration (file-based, no additional setup needed)
  h2:
    # H2 database file will be created in the plugin folder
    # No additional configuration needed

  # SQLite configuration (file-based, no additional setup needed)
  sqlite:
    # SQLite database file will be created in the plugin folder
    # No additional configuration needed

  # YAML configuration (file-based, no additional setup needed)
  yaml:
    # YAML data file will be created in the plugin folder
    # No additional configuration needed
```

## Migration

The plugin automatically handles data migration between storage types. When you change the storage type in the config:

1. The plugin will attempt to initialize the new storage provider
2. If successful, it will migrate all existing data to the new storage
3. If migration fails, it will fall back to YAML storage

## Admin Commands

### Storage Management
- `/clanadmin fix confirm` - Fix clan color inconsistencies
- `/clanadmin repair confirm` - Repair clan leadership issues

### Data Management
- `/clanadmin forcejoin <clan> <player>` - Force a player to join a clan
- `/clanadmin forceleave <player>` - Force a player to leave their clan
- `/clanadmin delete <clan>` - Delete a clan and all its data

## Backup and Restore

### YAML Storage
- Backup: Copy `plugins/VanguardClans/clans_data.yml`
- Restore: Replace the file and restart the server

### Database Storage (H2, SQLite, MySQL, MariaDB)
- Backup: Use your database's backup tools
- Restore: Use your database's restore tools

## Performance Considerations

1. **YAML**: Best for small servers (< 100 players)
2. **H2/SQLite**: Good for medium servers (100-500 players)
3. **MySQL/MariaDB**: Recommended for large servers (> 500 players)

## Troubleshooting

### Plugin won't start
- Check the storage configuration in `config.yml`
- Ensure database credentials are correct (for MySQL/MariaDB)
- Check server logs for specific error messages

### Data not appearing
- Verify the storage type is correctly set
- Check if data migration completed successfully
- Look for errors in the server logs

### Performance issues
- Consider switching to a more suitable storage type
- For database storage, ensure proper indexing
- Monitor server resources during peak usage

## Development

The storage system is built on a plugin architecture:

- `StorageProvider` - Interface defining storage operations
- `AbstractStorageProvider` - Base class with common functionality
- `*StorageProvider` - Specific implementations for each storage type
- `StorageFactory` - Factory class for creating storage providers

To add a new storage type:
1. Implement the `StorageProvider` interface
2. Add the new type to `StorageFactory`
3. Update the configuration documentation 