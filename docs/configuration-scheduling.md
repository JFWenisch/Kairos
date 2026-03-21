# Configuration: Check Scheduling

This page describes how check intervals and parallelism work.

## Resource Type Defaults

| Resource Type | Default Interval | Default Parallelism |
|---------------|------------------|---------------------|
| HTTP | 1 minute | 5 threads |
| DOCKER | 3600 minutes (60 h) | 2 threads |
| DOCKERREPOSITORY | 60 minutes | 1 thread |

Settings are managed in **Admin -> Resource Types**.

## How Scheduler Timing Works

- The scheduler loop runs every 30 seconds.
- A resource type run is dispatched when `now - lastRun >= interval`.
- Parallelism controls concurrent checks for that type.

## Startup Behavior

Kairos always performs an immediate check pass at startup, independent of configured intervals.

## DOCKERREPOSITORY Behavior

`DOCKERREPOSITORY` runs do not create direct check entries.

Instead, each run synchronizes discovered images into generated `DOCKER` resources in an auto-created group and removes resources that no longer exist in the upstream registry.
