-- =====================================================
-- INIT DATABASE MIGRATION (PostgreSQL Compatible)
-- =====================================================

-- Create users table first (no dependencies)
create table if not exists "users" (
    "id" bigserial primary key,
    "username" varchar not null unique,
    "email" varchar not null unique,
    "password" varchar,
    "auth_type" varchar not null,
    "is_active" boolean default true,
    "is_superadmin" boolean default false,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "deleted_at" timestamptz
);

-- Create roles table
create table if not exists "roles" (
    "id" bigserial primary key,
    "name" varchar not null unique,
    "description" varchar,
    "created_at" timestamptz default current_timestamp
);

-- Create user_roles junction table
create table if not exists "user_roles" (
    "user_id" bigint not null,
    "role_id" bigint not null,
    primary key ("user_id", "role_id"),
    foreign key ("user_id") references "users"("id") on delete cascade,
    foreign key ("role_id") references "roles"("id") on delete cascade
);

-- Create clusters table
create table if not exists "clusters" (
    "id" bigserial primary key,
    "name" varchar not null unique,
    "uid" varchar unique,
    "api_server" varchar,
    "auth_type" varchar not null,
    "kubeconfig" varchar,
    "is_active" boolean not null,
    "status" varchar,
    "version" varchar,
    "nodes" integer,
    "cpu" varchar,
    "memory" varchar,
    "provider" varchar,
    "created_at" timestamptz,
    "updated_at" timestamptz,
    "vuln_scan_enabled" boolean,
    "private_registry_user" varchar(255),
    "private_registry_password" varchar(255),
    "backup_enabled" boolean default true
);

-- Create pages table
create table if not exists "pages" (
    "id" bigserial primary key,
    "name" varchar not null unique,
    "display_name" varchar not null,
    "description" varchar,
    "resource_kind" varchar,
    "icon" varchar,
    "is_active" boolean,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz default current_timestamp,
    "is_namespace_scoped" boolean default false,
    "page_tier" integer default 3
);

-- Create actions table
create table if not exists "actions" (
    "id" bigserial primary key,
    "page_id" bigint not null,
    "name" varchar not null,
    "display_name" varchar not null,
    "description" varchar,
    "action_code" varchar not null,
    "resource_kind" varchar,
    "requires_write" boolean,
    "is_dangerous" boolean,
    "icon" varchar,
    "is_active" boolean,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz default current_timestamp,
    "category" varchar,
    unique ("page_id", "action_code"),
    foreign key ("page_id") references "pages"("id") on delete cascade
);

-- Create system_config table
create table if not exists "system_config" (
    "id" bigserial primary key,
    "config_key" varchar not null unique,
    "config_value" varchar,
    "setting_type" varchar default 'string',
    "description" varchar,
    "is_encrypted" boolean default false,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "config_category" varchar,
    "updated_by" bigint
);

-- Create backups table
create table if not exists "backups" (
    "id" bigserial primary key,
    "cluster_id" bigint not null,
    "cluster_name" varchar not null,
    "cluster_uid" varchar not null,
    "status" varchar not null,
    "backup_path" varchar,
    "total_resources" integer,
    "total_namespaces" integer,
    "size_bytes" bigint,
    "error_message" varchar,
    "started_at" timestamptz,
    "completed_at" timestamptz,
    "created_at" timestamptz default current_timestamp,
    "triggered_by" varchar,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- Create indexes for backups
create index if not exists "idx_backups_cluster_id" on "backups"("cluster_id");
create index if not exists "idx_backups_cluster_uid" on "backups"("cluster_uid");
create index if not exists "idx_backups_status" on "backups"("status");
create index if not exists "idx_backups_created_at" on "backups"("created_at");

-- Create nodes table
create table if not exists "nodes" (
    "id" bigserial primary key,
    "kind" varchar default 'Node',
    "api_version" varchar,
    "cluster_id" bigint not null,
    "name" varchar not null,
    "uid" varchar unique,
    "resource_version" varchar,
    "status" varchar,
    "roles" varchar,
    "cpu_capacity" varchar,
    "memory_capacity" varchar,
    "allocatable_cpu" varchar,
    "allocatable_memory" varchar,
    "kubelet_version" varchar,
    "os" varchar,
    "kernel" varchar,
    "capacity" varchar,
    "allocatable" varchar,
    "conditions" varchar,
    "addresses" varchar,
    "labels" varchar,
    "annotations" varchar,
    "managed_fields" varchar,
    "yaml" varchar,
    "is_deleted" boolean,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "deleted_at" timestamptz,
    "node_ip" varchar,
    "version" varchar,
    "owner_refs" varchar,
    "k8s_created_at" varchar,
    "unschedulable" boolean default false,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- Create indexes for nodes
create index if not exists "idx_nodes_cluster" on "nodes"("cluster_id");
create index if not exists "idx_nodes_uid" on "nodes"("uid");
create index if not exists "idx_nodes_status" on "nodes"("status");

-- Create namespaces table
create table if not exists "namespaces" (
    "id" bigserial primary key,
    "kind" varchar default 'Namespace',
    "api_version" varchar,
    "cluster_id" bigint not null,
    "name" varchar not null,
    "uid" varchar unique,
    "resource_version" varchar,
    "status" varchar,
    "labels" varchar,
    "annotations" varchar,
    "managed_fields" varchar,
    "yaml" varchar,
    "is_deleted" boolean,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "deleted_at" timestamptz,
    "k8s_created_at" varchar,
    "owner_refs" varchar,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- Create indexes for namespaces
create index if not exists "idx_namespaces_cluster" on "namespaces"("cluster_id");
create index if not exists "idx_namespaces_uid" on "namespaces"("uid");

-- Create pods table
create table if not exists "pods" (
    "id" bigserial primary key,
    "kind" varchar default 'Pod',
    "api_version" varchar,
    "cluster_id" bigint not null,
    "namespace" varchar not null,
    "name" varchar not null,
    "uid" varchar unique,
    "resource_version" varchar,
    "status" varchar,
    "phase" varchar,
    "node_name" varchar,
    "pod_ip" varchar,
    "host_ip" varchar,
    "qos_class" varchar,
    "restart_count" integer default 0,
    "containers" varchar,
    "owner" varchar,
    "owner_refs" varchar,
    "labels" varchar,
    "annotations" varchar,
    "managed_fields" varchar,
    "yaml" varchar,
    "is_deleted" boolean,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "deleted_at" timestamptz,
    "conditions" varchar,
    "init_containers" varchar,
    "generation" integer,
    "k8s_created_at" varchar,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- Create indexes for pods
create index if not exists "idx_pods_cluster_namespace" on "pods"("cluster_id", "namespace");
create index if not exists "idx_pods_uid" on "pods"("uid");
create index if not exists "idx_pods_status" on "pods"("status");

-- Create deployments table
create table if not exists "deployments" (
    "id" bigserial primary key,
    "kind" varchar default 'Deployment',
    "api_version" varchar,
    "cluster_id" bigint not null,
    "namespace" varchar not null,
    "name" varchar not null,
    "uid" varchar unique,
    "resource_version" varchar,
    "replicas_desired" integer,
    "replicas_available" integer,
    "replicas_ready" integer,
    "replicas_updated" integer,
    "strategy" varchar,
    "paused" boolean,
    "owner" varchar,
    "containers" varchar,
    "owner_refs" varchar,
    "labels" varchar,
    "annotations" varchar,
    "managed_fields" varchar,
    "yaml" varchar,
    "is_deleted" boolean,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "deleted_at" timestamptz,
    "available_replicas" integer,
    "desired_replicas" integer,
    "strategy_type" varchar,
    "generation" integer,
    "ready_replicas" integer,
    "replicas" integer,
    "updated_replicas" integer,
    "k8s_created_at" varchar,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- Create indexes for deployments
create index if not exists "idx_deployments_cluster_namespace" on "deployments"("cluster_id", "namespace");
create index if not exists "idx_deployments_uid" on "deployments"("uid");

-- Create services table
create table if not exists "services" (
    "id" bigserial primary key,
    "kind" varchar default 'Service',
    "api_version" varchar,
    "cluster_id" bigint not null,
    "namespace" varchar not null,
    "name" varchar not null,
    "uid" varchar unique,
    "resource_version" varchar,
    "type" varchar,
    "cluster_ip" varchar,
    "external_ip" varchar,
    "ports" varchar,
    "selector" varchar,
    "load_balancer_ip" varchar,
    "external_name" varchar,
    "session_affinity" varchar,
    "load_balancer_source_ranges" varchar,
    "ip_families" varchar,
    "ip_family_policy" varchar,
    "owner_refs" varchar,
    "labels" varchar,
    "annotations" varchar,
    "managed_fields" varchar,
    "yaml" varchar,
    "is_deleted" boolean,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "deleted_at" timestamptz,
    "external_ips" varchar,
    "generation" integer,
    "service_type" varchar,
    "k8s_created_at" varchar,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- Create indexes for services
create index if not exists "idx_services_cluster_namespace" on "services"("cluster_id", "namespace");
create index if not exists "idx_services_uid" on "services"("uid");
create index if not exists "idx_services_type" on "services"("type");

-- Create config_maps table
create table if not exists "config_maps" (
    "id" bigserial primary key,
    "kind" varchar default 'ConfigMap',
    "api_version" varchar,
    "cluster_id" bigint not null,
    "namespace" varchar not null,
    "name" varchar not null,
    "uid" varchar unique,
    "resource_version" varchar,
    "data_count" integer default 0,
    "immutable" boolean,
    "data" varchar,
    "binary_data" varchar,
    "owner_refs" varchar,
    "labels" varchar,
    "annotations" varchar,
    "managed_fields" varchar,
    "yaml" varchar,
    "is_deleted" boolean,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "deleted_at" timestamptz,
    "k8s_created_at" varchar,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- Create indexes for config_maps
create index if not exists "idx_config_maps_cluster_namespace" on "config_maps"("cluster_id", "namespace");
create index if not exists "idx_config_maps_uid" on "config_maps"("uid");

-- Create secrets table
create table if not exists "secrets" (
    "id" bigserial primary key,
    "kind" varchar default 'Secret',
    "api_version" varchar,
    "cluster_id" bigint not null,
    "namespace" varchar not null,
    "name" varchar not null,
    "uid" varchar unique,
    "resource_version" varchar,
    "secret_type" varchar,
    "data_count" integer default 0,
    "immutable" boolean,
    "owner_refs" varchar,
    "labels" varchar,
    "annotations" varchar,
    "managed_fields" varchar,
    "yaml" varchar,
    "is_deleted" boolean,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "deleted_at" timestamptz,
    "k8s_created_at" varchar,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- Create indexes for secrets
create index if not exists "idx_secrets_cluster_namespace" on "secrets"("cluster_id", "namespace");
create index if not exists "idx_secrets_uid" on "secrets"("uid");
create index if not exists "idx_secrets_type" on "secrets"("secret_type");

-- Create ingresses table
create table if not exists "ingresses" (
    "id" bigserial primary key,
    "kind" varchar default 'Ingress',
    "api_version" varchar,
    "cluster_id" bigint not null,
    "namespace" varchar not null,
    "name" varchar not null,
    "uid" varchar unique,
    "resource_version" varchar,
    "ingress_class" varchar,
    "hosts" varchar,
    "paths" varchar,
    "tls_enabled" boolean,
    "address" varchar,
    "rules" varchar,
    "owner_refs" varchar,
    "labels" varchar,
    "annotations" varchar,
    "managed_fields" varchar,
    "yaml" varchar,
    "is_deleted" boolean,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "deleted_at" timestamptz,
    "k8s_created_at" varchar,
    "generation" integer,
    "ingress_class_name" varchar,
    "status" varchar,
    "tls" varchar,
    "load_balancer_ip" varchar,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- Create indexes for ingresses
create index if not exists "idx_ingresses_cluster_namespace" on "ingresses"("cluster_id", "namespace");
create index if not exists "idx_ingresses_uid" on "ingresses"("uid");

-- Create jobs table
create table if not exists "jobs" (
    "id" bigserial primary key,
    "kind" varchar default 'Job',
    "api_version" varchar,
    "cluster_id" bigint not null,
    "namespace" varchar not null,
    "name" varchar not null,
    "uid" varchar unique,
    "resource_version" varchar,
    "completions" integer,
    "parallelism" integer,
    "active" integer default 0,
    "succeeded" integer default 0,
    "failed" integer default 0,
    "status" varchar,
    "owner_refs" varchar,
    "labels" varchar,
    "annotations" varchar,
    "managed_fields" varchar,
    "yaml" varchar,
    "is_deleted" boolean,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "deleted_at" timestamptz,
    "completion_time" timestamptz,
    "start_time" timestamptz,
    "generation" integer,
    "k8s_created_at" varchar,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- Create indexes for jobs
create index if not exists "idx_jobs_cluster_namespace" on "jobs"("cluster_id", "namespace");
create index if not exists "idx_jobs_uid" on "jobs"("uid");

-- Create statefulsets table
create table if not exists "statefulsets" (
    "id" bigserial primary key,
    "kind" varchar default 'StatefulSet',
    "api_version" varchar,
    "cluster_id" bigint not null,
    "namespace" varchar not null,
    "name" varchar not null,
    "uid" varchar unique,
    "resource_version" varchar,
    "generation" integer,
    "replicas" integer,
    "ready_replicas" integer,
    "current_replicas" integer,
    "updated_replicas" integer,
    "service_name" varchar,
    "update_strategy" varchar,
    "owner_refs" varchar,
    "labels" varchar,
    "annotations" varchar,
    "managed_fields" varchar,
    "yaml" varchar,
    "is_deleted" boolean,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "deleted_at" timestamptz,
    "k8s_created_at" varchar,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- Create indexes for statefulsets
create index if not exists "idx_statefulsets_cluster_namespace" on "statefulsets"("cluster_id", "namespace");
create index if not exists "idx_statefulsets_uid" on "statefulsets"("uid");

-- Create daemonsets table
create table if not exists "daemonsets" (
    "id" bigserial primary key,
    "kind" varchar default 'DaemonSet',
    "api_version" varchar,
    "cluster_id" bigint not null,
    "namespace" varchar not null,
    "name" varchar not null,
    "uid" varchar unique,
    "resource_version" varchar,
    "generation" integer,
    "desired_number_scheduled" integer,
    "current_number_scheduled" integer,
    "number_ready" integer,
    "number_available" integer,
    "node_selector" varchar,
    "update_strategy" varchar,
    "owner_refs" varchar,
    "labels" varchar,
    "annotations" varchar,
    "managed_fields" varchar,
    "yaml" varchar,
    "is_deleted" boolean,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "deleted_at" timestamptz,
    "k8s_created_at" varchar,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- Create indexes for daemonsets
create index if not exists "idx_daemonsets_cluster_namespace" on "daemonsets"("cluster_id", "namespace");
create index if not exists "idx_daemonsets_uid" on "daemonsets"("uid");

-- Create cronjobs table
create table if not exists "cronjobs" (
    "id" bigserial primary key,
    "kind" varchar default 'CronJob',
    "api_version" varchar,
    "cluster_id" bigint not null,
    "namespace" varchar not null,
    "name" varchar not null,
    "uid" varchar unique,
    "resource_version" varchar,
    "generation" integer,
    "schedule" varchar,
    "concurrency_policy" varchar,
    "suspend" boolean,
    "last_schedule_time" timestamptz,
    "active_jobs" varchar,
    "owner_refs" varchar,
    "labels" varchar,
    "annotations" varchar,
    "managed_fields" varchar,
    "yaml" varchar,
    "is_deleted" boolean,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "deleted_at" timestamptz,
    "k8s_created_at" varchar,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- Create indexes for cronjobs
create index if not exists "idx_cronjobs_cluster_namespace" on "cronjobs"("cluster_id", "namespace");
create index if not exists "idx_cronjobs_uid" on "cronjobs"("uid");

-- Create replicasets table
create table if not exists "replicasets" (
    "id" bigserial primary key,
    "kind" varchar default 'ReplicaSet',
    "api_version" varchar,
    "cluster_id" bigint not null,
    "namespace" varchar not null,
    "name" varchar not null,
    "uid" varchar unique,
    "resource_version" varchar,
    "generation" integer,
    "replicas" integer,
    "available_replicas" integer,
    "ready_replicas" integer,
    "owner_refs" varchar,
    "labels" varchar,
    "annotations" varchar,
    "managed_fields" varchar,
    "yaml" varchar,
    "is_deleted" boolean,
    "k8s_created_at" varchar,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "deleted_at" timestamptz,
    "min_ready_seconds" integer,
    "fully_labeled_replicas" integer,
    "observed_generation" integer,
    "conditions" varchar,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- Create indexes for replicasets
create index if not exists "idx_replicasets_cluster_namespace" on "replicasets"("cluster_id", "namespace");
create index if not exists "idx_replicasets_uid" on "replicasets"("uid");

-- Create persistent_volumes table
create table if not exists "persistent_volumes" (
    "id" bigserial primary key,
    "kind" varchar default 'PersistentVolume',
    "api_version" varchar,
    "cluster_id" bigint not null,
    "name" varchar not null,
    "uid" varchar unique,
    "resource_version" varchar,
    "generation" integer,
    "capacity" varchar,
    "access_modes" varchar,
    "persistent_volume_reclaim_policy" varchar,
    "storage_class_name" varchar,
    "volume_mode" varchar,
    "phase" varchar,
    "claim_ref" varchar,
    "persistent_volume_source" varchar,
    "mount_options" varchar,
    "node_affinity" varchar,
    "owner_refs" varchar,
    "labels" varchar,
    "annotations" varchar,
    "managed_fields" varchar,
    "yaml" varchar,
    "is_deleted" boolean,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "deleted_at" timestamptz,
    "k8s_created_at" varchar,
    "message" varchar,
    "reason" varchar,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- Create indexes for persistent_volumes
create index if not exists "idx_persistent_volumes_cluster" on "persistent_volumes"("cluster_id");
create index if not exists "idx_persistent_volumes_uid" on "persistent_volumes"("uid");
create index if not exists "idx_persistent_volumes_phase" on "persistent_volumes"("phase");

-- Create persistent_volume_claims table
create table if not exists "persistent_volume_claims" (
    "id" bigserial primary key,
    "kind" varchar default 'PersistentVolumeClaim',
    "api_version" varchar,
    "cluster_id" bigint not null,
    "namespace" varchar not null,
    "name" varchar not null,
    "uid" varchar unique,
    "resource_version" varchar,
    "status" varchar,
    "requested_size" varchar,
    "volume_name" varchar,
    "access_modes" varchar,
    "storage_class_name" varchar,
    "volume_mode" varchar,
    "owner_refs" varchar,
    "labels" varchar,
    "annotations" varchar,
    "managed_fields" varchar,
    "yaml" varchar,
    "is_deleted" boolean,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "deleted_at" timestamptz,
    "generation" integer,
    "k8s_created_at" varchar,
    "phase" varchar,
    "resources" varchar,
    "capacity" varchar,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- Create indexes for persistent_volume_claims
create index if not exists "idx_pvcs_cluster_namespace" on "persistent_volume_claims"("cluster_id", "namespace");
create index if not exists "idx_pvcs_uid" on "persistent_volume_claims"("uid");
create index if not exists "idx_pvcs_status" on "persistent_volume_claims"("status");

-- Create endpoint_slices table
create table if not exists "endpoint_slices" (
    "id" bigserial primary key,
    "kind" varchar default 'EndpointSlice',
    "api_version" varchar,
    "cluster_id" bigint not null,
    "namespace" varchar not null,
    "name" varchar not null,
    "uid" varchar unique,
    "resource_version" varchar,
    "service_name" varchar,
    "address_type" varchar,
    "address_count" integer default 0,
    "port_count" integer default 0,
    "endpoints" varchar,
    "ports" varchar,
    "owner_refs" varchar,
    "labels" varchar,
    "annotations" varchar,
    "managed_fields" varchar,
    "yaml" varchar,
    "is_deleted" boolean,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "deleted_at" timestamptz,
    "k8s_created_at" varchar,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- Create indexes for endpoint_slices
create index if not exists "idx_endpoint_slices_cluster_namespace" on "endpoint_slices"("cluster_id", "namespace");
create index if not exists "idx_endpoint_slices_uid" on "endpoint_slices"("uid");
create index if not exists "idx_endpoint_slices_service" on "endpoint_slices"("service_name");

-- Create events table
create table if not exists "events" (
    "id" bigserial primary key,
    "kind" varchar default 'Event',
    "api_version" varchar,
    "cluster_id" bigint not null,
    "namespace" varchar not null,
    "uid" varchar unique,
    "resource_version" varchar,
    "involved_object_kind" varchar,
    "involved_object_name" varchar,
    "type" varchar,
    "reason" varchar,
    "message" varchar,
    "count" integer default 1,
    "last_seen" varchar(255),
    "source" varchar,
    "yaml" varchar,
    "is_deleted" boolean,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "deleted_at" timestamptz,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- Create indexes for events
create index if not exists "idx_events_cluster_namespace" on "events"("cluster_id", "namespace");
create index if not exists "idx_events_involved_object" on "events"("involved_object_kind", "involved_object_name");
create index if not exists "idx_events_type" on "events"("type");

-- Create k8s_events table
create table if not exists "k8s_events" (
    "id" bigserial primary key,
    "kind" varchar default 'Event',
    "api_version" varchar,
    "cluster_id" bigint not null,
    "namespace" varchar not null,
    "name" varchar not null,
    "uid" varchar unique,
    "resource_version" varchar,
    "type" varchar,
    "reason" varchar,
    "message" varchar,
    "source" varchar,
    "involved_object" varchar,
    "involved_object_kind" varchar,
    "involved_object_name" varchar,
    "count" integer default 1,
    "first_timestamp" timestamptz,
    "last_timestamp" timestamptz,
    "last_seen" varchar(255),
    "managed_fields" varchar,
    "yaml" varchar,
    "is_deleted" boolean,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "deleted_at" timestamptz,
    "k8s_created_at" varchar,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- Create indexes for k8s_events
create index if not exists "idx_events_uid" on "k8s_events"("uid");
create index if not exists "idx_events_timestamp" on "k8s_events"("last_timestamp");

-- Create leases table
create table if not exists "leases" (
    "id" bigserial primary key,
    "kind" varchar default 'Lease',
    "api_version" varchar,
    "cluster_id" bigint not null,
    "namespace" varchar not null,
    "name" varchar not null,
    "uid" varchar unique,
    "resource_version" varchar,
    "holder_identity" varchar,
    "lease_duration_seconds" integer,
    "acquire_time" timestamptz,
    "renew_time" timestamptz,
    "owner_refs" varchar,
    "labels" varchar,
    "annotations" varchar,
    "managed_fields" varchar,
    "yaml" varchar,
    "is_deleted" boolean,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "deleted_at" timestamptz,
    "k8s_created_at" varchar,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- Create indexes for leases
create index if not exists "idx_leases_cluster_namespace" on "leases"("cluster_id", "namespace");
create index if not exists "idx_leases_uid" on "leases"("uid");

-- Create service_accounts table
create table if not exists "service_accounts" (
    "id" bigserial primary key,
    "kind" varchar default 'ServiceAccount',
    "api_version" varchar,
    "cluster_id" bigint not null,
    "name" varchar not null,
    "uid" varchar unique,
    "namespace" varchar not null,
    "resource_version" varchar,
    "generation" integer,
    "owner_refs" varchar,
    "managed_fields" varchar,
    "secrets" varchar,
    "image_pull_secrets" varchar,
    "automount_service_account_token" boolean,
    "labels" varchar,
    "annotations" varchar,
    "yaml" varchar,
    "is_deleted" boolean default false,
    "k8s_created_at" varchar,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "deleted_at" timestamptz,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- Create cluster_roles table
create table if not exists "cluster_roles" (
    "id" bigserial primary key,
    "kind" varchar default 'ClusterRole',
    "api_version" varchar,
    "cluster_id" bigint not null,
    "name" varchar not null,
    "uid" varchar unique,
    "namespace" varchar,
    "resource_version" varchar,
    "generation" integer,
    "owner_refs" varchar,
    "managed_fields" varchar,
    "rules" varchar,
    "aggregation_rule" varchar,
    "labels" varchar,
    "annotations" varchar,
    "yaml" varchar,
    "is_deleted" boolean default false,
    "k8s_created_at" varchar,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "deleted_at" timestamptz,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- Create cluster_role_bindings table
create table if not exists "cluster_role_bindings" (
    "id" bigserial primary key,
    "kind" varchar default 'ClusterRoleBinding',
    "api_version" varchar,
    "cluster_id" bigint not null,
    "name" varchar not null,
    "uid" varchar unique,
    "namespace" varchar,
    "resource_version" varchar,
    "generation" integer,
    "owner_refs" varchar,
    "managed_fields" varchar,
    "role_ref" varchar,
    "subjects" varchar,
    "labels" varchar,
    "annotations" varchar,
    "yaml" varchar,
    "is_deleted" boolean default false,
    "k8s_created_at" varchar,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "deleted_at" timestamptz,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- Create exec_logs table
create table if not exists "exec_logs" (
    "id" bigserial primary key,
    "user_id" bigint not null,
    "cluster_id" varchar not null,
    "namespace" varchar not null,
    "pod_name" varchar not null,
    "container_name" varchar,
    "command" varchar not null,
    "is_allowed" boolean,
    "created_at" timestamptz default current_timestamp,
    foreign key ("user_id") references "users"("id")
);

-- Create indexes for exec_logs
create index if not exists "idx_exec_logs_user" on "exec_logs"("user_id");

-- Create exec_sessions table
create table if not exists "exec_sessions" (
    "id" bigserial primary key,
    "user_id" bigint not null,
    "session_id" varchar not null unique,
    "cluster_id" varchar not null,
    "namespace" varchar not null,
    "pod_name" varchar not null,
    "status" varchar default 'active',
    "created_at" timestamptz default current_timestamp,
    foreign key ("user_id") references "users"("id")
);

-- Create indexes for exec_sessions
create index if not exists "idx_exec_sessions_user" on "exec_sessions"("user_id");

-- Create exec_session_recordings table
create table if not exists "exec_session_recordings" (
    "id" bigserial primary key,
    "session_uid" varchar not null,
    "event_data" varchar not null,
    "created_at" timestamptz default current_timestamp,
    foreign key ("session_uid") references "exec_sessions"("session_id")
);

-- Create ui_permissions table
create table if not exists "ui_permissions" (
    "id" bigserial primary key,
    "user_id" bigint not null unique,
    "pages" varchar not null,
    "features" varchar,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    foreign key ("user_id") references "users"("id") on delete cascade
);

-- Create indexes for ui_permissions
create index if not exists "idx_ui_permissions_user" on "ui_permissions"("user_id");

-- Create vulnerability_results table
create table if not exists "vulnerability_results" (
    "id" bigserial primary key,
    "cluster_id" varchar not null,
    "namespace" varchar not null,
    "image" varchar not null,
    "resource_name" varchar,
    "resource_kind" varchar,
    "critical_count" integer default 0,
    "high_count" integer default 0,
    "medium_count" integer default 0,
    "low_count" integer default 0,
    "severity_score" double precision,
    "last_scan_time" timestamptz default current_timestamp,
    "result_json" varchar,
    "affected_resources" varchar,
    "registry_matched" boolean default null,
    "matched_registry" varchar(500) default null,
    unique ("cluster_id", "namespace", "resource_name", "resource_kind", "image")
);

-- Create indexes for vulnerability_results
create index if not exists "idx_vuln_resource" on "vulnerability_results"("cluster_id", "namespace", "resource_name", "resource_kind");

-- Create cluster_eye_results table
create table if not exists "cluster_eye_results" (
    "id" bigserial primary key,
    "cluster_uid" varchar(255) not null,
    "namespace" varchar(255) not null,
    "workload_kind" varchar(50) not null,
    "workload_name" varchar(255) not null,
    "findings" varchar,
    "critical_count" integer default 0 not null,
    "high_count" integer default 0 not null,
    "medium_count" integer default 0 not null,
    "total_count" integer default 0 not null,
    "last_scanned_at" timestamptz not null,
    "created_at" timestamptz default current_timestamp,
    unique ("cluster_uid", "namespace", "workload_kind", "workload_name")
);

-- Create indexes for cluster_eye_results
create index if not exists "idx_eye_cluster" on "cluster_eye_results"("cluster_uid");
create index if not exists "idx_eye_cluster_ns" on "cluster_eye_results"("cluster_uid", "namespace");
create index if not exists "idx_eye_cluster_kind" on "cluster_eye_results"("cluster_uid", "workload_kind");
create index if not exists "idx_eye_critical" on "cluster_eye_results"("cluster_uid", "critical_count" desc);
create index if not exists "idx_eye_last_scanned" on "cluster_eye_results"("cluster_uid", "last_scanned_at" desc);

-- Create audit_logs table
create table if not exists "audit_logs" (
    "id" bigserial primary key,
    "username" varchar(255) not null,
    "timestamp" timestamptz not null,
    "action" varchar(255) not null,
    "details" varchar,
    "cluster_uid" varchar(255),
    "cluster_name" varchar(255)
);

-- Create indexes for audit_logs
create index if not exists "idx_audit_logs_cluster_uid" on "audit_logs"("cluster_uid");

-- Create pod_metrics table
create table if not exists "pod_metrics" (
    "id" bigserial primary key,
    "cluster_uid" varchar(255) not null,
    "namespace" varchar(255) not null,
    "pod_name" varchar(255) not null,
    "cpu_millicores" integer default 0 not null,
    "memory_bytes" bigint default 0 not null,
    "collected_at" timestamptz not null
);

-- Create indexes for pod_metrics
create index if not exists "idx_pm_cluster_ns_pod_time" on "pod_metrics"("cluster_uid", "namespace", "pod_name", "collected_at");
create index if not exists "idx_pm_cluster_time" on "pod_metrics"("cluster_uid", "collected_at");
create index if not exists "idx_pm_collected_at" on "pod_metrics"("collected_at");

-- Create casbin_rule table for rbac
create table if not exists "casbin_rule" (
    "id" bigserial primary key,
    "ptype" varchar(100) not null,
    "v0" varchar(512),
    "v1" varchar(512),
    "v2" varchar(512),
    "v3" varchar(512),
    "v4" varchar(512),
    "v5" varchar(512)
);

-- Create indexes for casbin_rule
create index if not exists "idx_casbin_ptype" on "casbin_rule"("ptype");
create index if not exists "idx_casbin_v0" on "casbin_rule"("v0");
create index if not exists "idx_casbin_v1" on "casbin_rule"("v1");

-- Create role_templates table
create table if not exists "role_templates" (
    "id" bigserial primary key,
    "name" varchar(100) not null unique,
    "display_name" varchar(200),
    "description" varchar,
    "color" varchar(20) default '#6366f1',
    "is_active" boolean default true not null,
    "created_by" bigint,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz
);

-- Create allowed_commands table
create table if not exists "allowed_commands" (
    "id" bigserial primary key,
    "role_id" bigint,
    "command_pattern" varchar not null,
    "description" varchar,
    "created_at" timestamptz default current_timestamp,
    "role_template_name" varchar(100),
    foreign key ("role_id") references "roles"("id")
);

-- Create hpas table
create table if not exists "hpas" (
    "id" bigserial primary key,
    "kind" varchar(50) default 'HorizontalPodAutoscaler' not null,
    "api_version" varchar(100),
    "cluster_id" bigint not null,
    "namespace" varchar(255) not null,
    "name" varchar(255) not null,
    "uid" varchar(255) unique,
    "resource_version" varchar(100),
    "generation" integer,
    "scale_target_ref" varchar,
    "min_replicas" integer,
    "max_replicas" integer,
    "current_replicas" integer,
    "desired_replicas" integer,
    "metrics" varchar,
    "current_metrics" varchar,
    "conditions" varchar,
    "behavior" varchar,
    "owner_refs" varchar,
    "labels" varchar,
    "annotations" varchar,
    "managed_fields" varchar,
    "is_deleted" boolean default false not null,
    "k8s_created_at" varchar(100),
    "created_at" timestamptz,
    "updated_at" timestamptz,
    "deleted_at" timestamptz,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- Create indexes for hpas
create index if not exists "idx_hpa_cluster" on "hpas"("cluster_id");
create index if not exists "idx_hpa_cluster_ns" on "hpas"("cluster_id", "namespace");
create index if not exists "idx_hpa_deleted" on "hpas"("cluster_id", "is_deleted");

-- Create network_flow_rotation_state table
create table if not exists "network_flow_rotation_state" (
    "id" integer not null primary key,
    "active_table" varchar(50) default 'network_flows_1' not null,
    "last_rotation" timestamptz not null
);

-- Insert default rotation state
insert into "network_flow_rotation_state" ("id", "active_table", "last_rotation")
values (1, 'network_flows_1', current_timestamp)
on conflict ("id") do nothing;

-- Create network_flows_1 table
create table if not exists "network_flows_1" (
    "id" bigserial primary key,
    "flow_id" varchar(255) not null,
    "cluster_uid" varchar(255) not null,
    "flow_type" varchar(100) not null,
    "timestamp" timestamptz not null,
    "source_pod_name" varchar(255),
    "source_namespace" varchar(255),
    "source_kind" varchar(100),
    "source_ip" varchar(64) not null,
    "source_port" integer not null,
    "source_node_name" varchar(255),
    "source_ingress" boolean default false,
    "source_egress" boolean default false,
    "source_drop" boolean default false,
    "source_pass" boolean default false,
    "destination_pod_name" varchar(255),
    "destination_namespace" varchar(255),
    "destination_kind" varchar(100),
    "destination_ip" varchar(64) not null,
    "destination_port" integer not null,
    "destination_node_name" varchar(255),
    "destination_ingress" boolean default false,
    "destination_egress" boolean default false,
    "destination_drop" boolean default false,
    "destination_pass" boolean default false,
    "service_name" varchar(255),
    "service_namespace" varchar(255),
    "backend_pod_name" varchar(255),
    "backend_pod_namespace" varchar(255),
    "protocol" varchar(20) not null,
    "tcp_flags" integer,
    "bytes" bigint default 0,
    "interface_name" varchar(100),
    "direction" varchar(20),
    "l7_protocol" varchar(20),
    "l7_method" varchar(20),
    "l7_host" varchar(512),
    "l7_url" varchar(2048),
    "l7_path" varchar(2048),
    "l7_status_code" integer,
    "l7_content_type" varchar(255),
    "node_name" varchar(255),
    "created_at" timestamptz default current_timestamp
);

-- Create indexes for network_flows_1
create index if not exists "idx_nf1_cluster_uid" on "network_flows_1"("cluster_uid");
create index if not exists "idx_nf1_timestamp" on "network_flows_1"("timestamp");
create index if not exists "idx_nf1_cluster_time" on "network_flows_1"("cluster_uid", "timestamp");
create index if not exists "idx_nf1_source_ns" on "network_flows_1"("source_namespace");
create index if not exists "idx_nf1_destination_ns" on "network_flows_1"("destination_namespace");
create index if not exists "idx_nf1_flow_type" on "network_flows_1"("flow_type");
create index if not exists "idx_nf1_protocol" on "network_flows_1"("protocol");

-- Create network_flows_2 table (identical structure)
create table if not exists "network_flows_2" (
    "id" bigserial primary key,
    "flow_id" varchar(255) not null,
    "cluster_uid" varchar(255) not null,
    "flow_type" varchar(100) not null,
    "timestamp" timestamptz not null,
    "source_pod_name" varchar(255),
    "source_namespace" varchar(255),
    "source_kind" varchar(100),
    "source_ip" varchar(64) not null,
    "source_port" integer not null,
    "source_node_name" varchar(255),
    "source_ingress" boolean default false,
    "source_egress" boolean default false,
    "source_drop" boolean default false,
    "source_pass" boolean default false,
    "destination_pod_name" varchar(255),
    "destination_namespace" varchar(255),
    "destination_kind" varchar(100),
    "destination_ip" varchar(64) not null,
    "destination_port" integer not null,
    "destination_node_name" varchar(255),
    "destination_ingress" boolean default false,
    "destination_egress" boolean default false,
    "destination_drop" boolean default false,
    "destination_pass" boolean default false,
    "service_name" varchar(255),
    "service_namespace" varchar(255),
    "backend_pod_name" varchar(255),
    "backend_pod_namespace" varchar(255),
    "protocol" varchar(20) not null,
    "tcp_flags" integer,
    "bytes" bigint default 0,
    "interface_name" varchar(100),
    "direction" varchar(20),
    "l7_protocol" varchar(20),
    "l7_method" varchar(20),
    "l7_host" varchar(512),
    "l7_url" varchar(2048),
    "l7_path" varchar(2048),
    "l7_status_code" integer,
    "l7_content_type" varchar(255),
    "node_name" varchar(255),
    "created_at" timestamptz default current_timestamp
);

-- Create indexes for network_flows_2
create index if not exists "idx_nf2_cluster_uid" on "network_flows_2"("cluster_uid");
create index if not exists "idx_nf2_timestamp" on "network_flows_2"("timestamp");
create index if not exists "idx_nf2_cluster_time" on "network_flows_2"("cluster_uid", "timestamp");
create index if not exists "idx_nf2_source_ns" on "network_flows_2"("source_namespace");
create index if not exists "idx_nf2_destination_ns" on "network_flows_2"("destination_namespace");
create index if not exists "idx_nf2_flow_type" on "network_flows_2"("flow_type");
create index if not exists "idx_nf2_protocol" on "network_flows_2"("protocol");

-- Create network_flows view
create or replace view "network_flows" as
select * from "network_flows_1"
union all
select * from "network_flows_2";

-- Create network_topology_edges table
create table if not exists "network_topology_edges" (
    "id" bigserial primary key,
    "cluster_id" bigint,
    "source_type" varchar not null,
    "source_name" varchar not null,
    "source_namespace" varchar,
    "target_type" varchar not null,
    "target_name" varchar not null,
    "target_namespace" varchar,
    "protocol" varchar,
    "port" integer,
    "flow_count" bigint default 0,
    "total_bytes" bigint default 0,
    "last_seen" timestamptz,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz default current_timestamp,
    "cluster_uid" varchar(255),
    "service_name" varchar,
    "service_namespace" varchar,
    "flow_type" varchar,
    "backend_pod_name" varchar,
    "backend_pod_namespace" varchar,
    unique ("cluster_id", "source_type", "source_name", "source_namespace", "target_type", "target_name", "target_namespace", "protocol", "port"),
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- Create indexes for network_topology_edges
create index if not exists "idx_topology_cluster" on "network_topology_edges"("cluster_id");
create index if not exists "idx_topology_source" on "network_topology_edges"("source_type", "source_name");
create index if not exists "idx_topology_target" on "network_topology_edges"("target_type", "target_name");
create index if not exists "idx_topology_last_seen" on "network_topology_edges"("last_seen");
create index if not exists "idx_network_topology_edges_cluster_uid" on "network_topology_edges"("cluster_uid");
create index if not exists "idx_topology_edges_ingress" on "network_topology_edges"("cluster_uid", "target_namespace");
create index if not exists "idx_topology_edges_egress" on "network_topology_edges"("cluster_uid", "source_namespace");
create index if not exists "idx_topology_edges_labels" on "network_topology_edges"("cluster_uid", "target_namespace", "source_namespace");

-- Create network_flow_stats table
create table if not exists "network_flow_stats" (
    "id" bigserial primary key,
    "cluster_id" bigint,
    "period_start" timestamptz not null,
    "period_end" timestamptz not null,
    "granularity" varchar not null,
    "flow_type" varchar,
    "source_namespace" varchar,
    "destination_namespace" varchar,
    "protocol" varchar,
    "flow_count" bigint default 0,
    "total_bytes" bigint default 0,
    "created_at" timestamptz default current_timestamp,
    "cluster_uid" varchar(255),
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- Create indexes for network_flow_stats
create index if not exists "idx_flow_stats_cluster" on "network_flow_stats"("cluster_id");
create index if not exists "idx_flow_stats_period" on "network_flow_stats"("period_start", "period_end");
create index if not exists "idx_network_flow_stats_cluster_uid" on "network_flow_stats"("cluster_uid");
create index if not exists "idx_flow_stats_granularity" on "network_flow_stats"("granularity");

-- =====================================================
-- Insert default data
-- =====================================================

-- Insert default admin user (password: change_me)
insert into "users" ("username", "email", "auth_type", "is_active", "is_superadmin") 
select 'admin', 'admin@localhost', 'local', true, true
where not exists (select 1 from "users" where "username" = 'admin');

-- Insert default roles
insert into "roles" ("name", "description") values 
('SUPERADMIN', 'Full system administrator'),
('viewer', 'Read-only access'),
('editor', 'Can edit resources')
on conflict ("name") do nothing;

-- Insert role templates
insert into "role_templates" ("name", "display_name", "description", "color", "is_active") values 
('SUPERADMIN', 'Super Administrator', 'Full access to everything', '#dc2626',true),
('viewer', 'Viewer', 'Read-only access to all resources', '#3b82f6', true),
('editor', 'Editor', 'Can edit and view resources', '#10b981', true)
on conflict ("name") do nothing;

insert into casbin_rule (ptype, v0, v1, v2, v3, v4) values ('p', 'SUPERADMIN', '*', '*', '*', '*');

-- Assign admin role to admin user
insert into "user_roles" ("user_id", "role_id")
select u."id", r."id" from "users" u, "roles" r 
where u."username" = 'admin' and r."name" = 'SUPERADMIN'
on conflict ("user_id", "role_id") do nothing;

-- appcreator_registry_connections table
create table if not exists "appcreator_registry_connections" (
    "id" uuid default gen_random_uuid() primary key,
    "cluster_uid" varchar(255) not null,
    "user_id" bigint not null,
    "registry_type" varchar(50) not null,
    "name" varchar(255) not null,
    "server_url" varchar(500),
    "username" varchar(255) not null,
    "password_token" varchar(2000) not null,
    "image_prefix" varchar(255),
    "is_default" boolean default false,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz default current_timestamp
);

-- Create indexes for appcreator_registry_connections
create index if not exists "idx_appcreator_reg_conn_cluster" on "appcreator_registry_connections"("cluster_uid", "user_id");

-- appcreator_git_connections table
create table if not exists "appcreator_git_connections" (
    "id" uuid default gen_random_uuid() primary key,
    "user_id" bigint not null,
    "provider" varchar(50) not null,
    "name" varchar(255) not null,
    "access_token" varchar(2000) not null,
    "base_url" varchar(500),
    "is_default" boolean default false,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz default current_timestamp,
    "cluster_uid" varchar(255)
);

-- Create indexes for appcreator_git_connections
create index if not exists "idx_appcreator_git_conn_cluster" on "appcreator_git_connections"("cluster_uid");
create index if not exists "idx_git_conn_user" on "appcreator_git_connections"("user_id");

-- appcreator_apps table
create table if not exists "appcreator_apps" (
    "id" uuid default gen_random_uuid() primary key,
    "cluster_uid" varchar(255) not null,
    "name" varchar(255) not null,
    "description" varchar(500),
    "namespace" varchar(255) not null,
    "workload_type" varchar(50) not null,
    "config" text not null,
    "status" varchar(50) default 'draft',
    "template_id" uuid,
    "created_by" bigint,
    "updated_by" bigint,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz default current_timestamp,
    "git_connection_id" uuid,
    "git_repo" varchar(500),
    "git_base_branch" varchar(255),
    "gitops_path" varchar(500) default 'k8s'
);

-- Create indexes for appcreator_apps
create index if not exists "idx_appcreator_apps_cluster" on "appcreator_apps"("cluster_uid");
create index if not exists "idx_appcreator_apps_status" on "appcreator_apps"("cluster_uid", "status");

-- appcreator_deploy_history table
create table if not exists "appcreator_deploy_history" (
    "id" uuid default gen_random_uuid() primary key,
    "app_id" uuid not null,
    "cluster_uid" varchar(255) not null,
    "deploy_type" varchar(50) not null,
    "status" varchar(50) not null,
    "git_repo" varchar(255),
    "git_branch" varchar(255),
    "git_pr_url" varchar(500),
    "git_commit_sha" varchar(100),
    "resource_count" integer,
    "yaml_snapshot" text,
    "error_message" text,
    "deployed_by" bigint,
    "created_at" timestamptz default current_timestamp,
    foreign key ("app_id") references "appcreator_apps"("id") on delete cascade
);

-- Create indexes for appcreator_deploy_history
create index if not exists "idx_appcreator_deploy_app" on "appcreator_deploy_history"("app_id");
create index if not exists "idx_appcreator_deploy_cluster" on "appcreator_deploy_history"("cluster_uid");

-- appcreator_drafts table
create table if not exists "appcreator_drafts" (
    "id" uuid default gen_random_uuid() primary key,
    "cluster_uid" varchar(255) not null,
    "app_id" uuid,
    "wizard_state" text not null,
    "current_step" integer default 1,
    "created_by" bigint,
    "updated_at" timestamptz default current_timestamp,
    foreign key ("app_id") references "appcreator_apps"("id") on delete cascade
);

-- Create indexes for appcreator_drafts
create index if not exists "idx_appcreator_drafts_cluster" on "appcreator_drafts"("cluster_uid");

-- appcreator_templates table
create table if not exists "appcreator_templates" (
    "id" uuid default gen_random_uuid() primary key,
    "cluster_uid" varchar(255),
    "name" varchar(255) not null,
    "description" varchar(500),
    "category" varchar(100),
    "icon" varchar(100),
    "config" text not null,
    "is_public" boolean default false,
    "created_by" bigint,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz default current_timestamp
);

-- Create indexes for appcreator_templates
create index if not exists "idx_appcreator_templates_cluster" on "appcreator_templates"("cluster_uid");

-- federations table
create table if not exists "federations" (
    "id" bigserial primary key,
    "master_cluster_id" bigint not null,
    "name" varchar(255) not null,
    "status" varchar(50),
    "created_at" timestamptz,
    "updated_at" timestamptz,
    foreign key ("master_cluster_id") references "clusters"("id") on delete cascade
);

-- federation_members table
create table if not exists "federation_members" (
    "id" bigserial primary key,
    "federation_id" bigint not null,
    "member_cluster_id" bigint not null,
    foreign key ("federation_id") references "federations"("id") on delete cascade,
    foreign key ("member_cluster_id") references "clusters"("id") on delete cascade
);

-- federation_resources table
create table if not exists "federation_resources" (
    "id" bigserial primary key,
    "federation_id" bigint not null,
    "kind" varchar(100) not null,
    "namespace" varchar(255) not null,
    "name" varchar(255) not null,
    "sync_status" varchar(50),
    "error_message" varchar,
    "last_error_time" timestamptz,
    "last_sync_time" timestamptz,
    "previous_state_yaml" varchar,
    "dependency_status" varchar,
    "backup_yaml" varchar,
    foreign key ("federation_id") references "federations"("id") on delete cascade
);

-- generated_network_policies table
create table if not exists "generated_network_policies" (
    "id" bigserial primary key,
    "cluster_uid" varchar not null,
    "namespace" varchar not null,
    "name" varchar not null,
    "policy_type" varchar not null,
    "pod_selector" varchar not null,
    "rules" varchar not null,
    "yaml_content" varchar not null,
    "status" varchar default 'draft' not null,
    "description" varchar,
    "created_by" varchar,
    "applied_at" timestamptz,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz default current_timestamp
);

-- Create indexes for generated_network_policies
create index if not exists "idx_gnp_cluster_uid" on "generated_network_policies"("cluster_uid");
create index if not exists "idx_gnp_namespace" on "generated_network_policies"("namespace");
create index if not exists "idx_gnp_policy_type" on "generated_network_policies"("policy_type");
create index if not exists "idx_gnp_status" on "generated_network_policies"("status");
create index if not exists "idx_gnp_cluster_ns" on "generated_network_policies"("cluster_uid", "namespace");

-- network_policy_migrations table
create table if not exists "network_policy_migrations" (
    "id" bigserial primary key,
    "policy_id" bigint not null,
    "version" integer not null,
    "action" varchar not null,
    "previous_yaml" varchar,
    "new_yaml" varchar,
    "change_description" varchar,
    "applied_by" varchar,
    "applied_at" timestamptz default current_timestamp,
    "rollback_at" timestamptz,
    "rolled_back_by" varchar,
    "created_at" timestamptz default current_timestamp,
    foreign key ("policy_id") references "generated_network_policies"("id") on delete cascade
);

-- Create indexes for network_policy_migrations
create index if not exists "idx_npm_policy_id" on "network_policy_migrations"("policy_id");
create index if not exists "idx_npm_version" on "network_policy_migrations"("version");
create index if not exists "idx_npm_action" on "network_policy_migrations"("action");
create index if not exists "idx_npm_applied_at" on "network_policy_migrations"("applied_at");

-- helm_repositories table
create table if not exists "helm_repositories" (
    "id" uuid default gen_random_uuid() primary key,
    "cluster_uid" varchar(255) not null,
    "name" varchar(255) not null,
    "url" varchar(1024) not null,
    "is_private" boolean default false not null,
    "username" varchar(255),
    "password" varchar(1024),
    "created_at" timestamptz,
    "updated_at" timestamptz,
    unique ("cluster_uid", "name")
);

-- image_registry_credentials table
create table if not exists "image_registry_credentials" (
    "id" bigserial primary key,
    "cluster_uid" varchar(255) not null,
    "registry_url" varchar(500) not null,
    "username" varchar(255) not null,
    "password" varchar(500) not null,
    "description" varchar(500),
    "created_at" timestamptz default current_timestamp not null,
    "updated_at" timestamptz default current_timestamp not null,
    unique ("cluster_uid", "registry_url")
);

-- Create indexes for image_registry_credentials
create index if not exists "idx_irc_cluster" on "image_registry_credentials"("cluster_uid");

-- user_policies table
create table if not exists "user_policies" (
    "id" bigserial primary key,
    "user_id" bigint not null,
    "subject_type" varchar default 'user' not null,
    "subject_name" varchar not null,
    "assignments_json" varchar default '[]' not null,
    "ui_permissions_json" varchar default '{"pages":[],"features":[]}',
    "roles_json" varchar default '[]',
    "is_active" boolean,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "created_by" bigint,
    foreign key ("user_id") references "users"("id") on delete cascade,
    foreign key ("created_by") references "users"("id")
);

-- Create indexes for user_policies
create index if not exists "idx_user_policies_user_id" on "user_policies"("user_id");
create index if not exists "idx_user_policies_subject_name" on "user_policies"("subject_name");
create index if not exists "idx_user_policies_is_active" on "user_policies"("is_active");

-- policy_assignments table
create table if not exists "policy_assignments" (
    "id" bigserial primary key,
    "policy_id" bigint not null,
    "cluster_uid" varchar not null,
    "namespace" varchar not null,
    "resource_kind" varchar not null,
    "name_pattern" varchar not null,
    "actions_json" varchar not null,
    "created_at" timestamptz default current_timestamp,
    foreign key ("policy_id") references "user_policies"("id") on delete cascade
);

-- Create indexes for policy_assignments
create index if not exists "idx_policy_assignments_policy" on "policy_assignments"("policy_id");
create index if not exists "idx_policy_assignments_cluster" on "policy_assignments"("cluster_uid");

-- security_rules table
create table if not exists "security_rules" (
    "id" bigserial primary key,
    "cluster_uid" varchar(500) not null,
    "name" varchar(500) not null,
    "description" varchar,
    "priority" varchar(20) not null,
    "condition_json" varchar not null,
    "output" varchar,
    "enabled" boolean default true,
    "tags_json" varchar,
    "rule_type" varchar(50) default 'process',
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "created_by" bigint,
    "is_active" boolean default true,
    unique ("cluster_uid", "name"),
    foreign key ("created_by") references "users"("id") on delete set null
);

-- Create indexes for security_rules
create index if not exists "idx_security_rules_cluster_enabled" on "security_rules"("cluster_uid", "enabled");
create index if not exists "idx_security_rules_priority" on "security_rules"("priority");
create index if not exists "idx_security_rules_created_at" on "security_rules"("created_at");

-- security_alerts table
create table if not exists "security_alerts" (
    "id" bigserial primary key,
    "cluster_uid" varchar(500) not null,
    "event_type" varchar(50) not null,
    "priority" varchar(20) not null,
    "rule_name" varchar(500) not null,
    "rule_description" varchar,
    "output" varchar not null,
    "namespace_name" varchar(500),
    "pod_name" varchar(500),
    "container_id" varchar(500),
    "event_data_json" varchar,
    "fingerprint" varchar(500),
    "tags_json" varchar,
    "is_acknowledged" boolean default false,
    "acknowledged_by" bigint,
    "acknowledged_at" timestamptz,
    "acknowledgment_note" varchar,
    "resolved" boolean default false,
    "resolved_by" bigint,
    "resolved_at" timestamptz,
    "resolution_note" varchar,
    "created_at" timestamptz default current_timestamp,
    foreign key ("acknowledged_by") references "users"("id") on delete set null,
    foreign key ("resolved_by") references "users"("id") on delete set null
);

-- Create indexes for security_alerts
create index if not exists "idx_security_alerts_cluster" on "security_alerts"("cluster_uid", "created_at");
create index if not exists "idx_security_alerts_priority" on "security_alerts"("priority");
create index if not exists "idx_security_alerts_pod" on "security_alerts"("pod_name");
create index if not exists "idx_security_alerts_namespace" on "security_alerts"("namespace_name");
create index if not exists "idx_security_alerts_fingerprint" on "security_alerts"("fingerprint");
create index if not exists "idx_security_alerts_resolved" on "security_alerts"("resolved");
create index if not exists "idx_security_alerts_acknowledged" on "security_alerts"("is_acknowledged");

-- monitoring_configs table
create table if not exists "monitoring_configs" (
    "id" bigserial primary key,
    "cluster_uid" varchar(500) not null unique,
    "enable_execve" boolean default true,
    "enable_open" boolean default true,
    "enable_openat" boolean default false,
    "enable_connect" boolean default true,
    "enable_bind" boolean default true,
    "enable_unlink" boolean default false,
    "enable_unlinkat" boolean default false,
    "enable_write" boolean default false,
    "enable_link" boolean default false,
    "enable_rename" boolean default false,
    "enable_mkdir" boolean default false,
    "enable_rmdir" boolean default false,
    "enable_xattr" boolean default false,
    "enable_clone" boolean default true,
    "enable_fork" boolean default true,
    "additional_config_json" varchar,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "updated_by" bigint,
    "enable_ptrace" boolean default false,
    "enable_mount" boolean default false,
    "enable_accept" boolean default false,
    foreign key ("updated_by") references "users"("id") on delete set null
);

-- k8sroles table (kubernetes roles)
create table if not exists "k8sroles" (
    "id" bigserial primary key,
    "kind" varchar default 'Role',
    "api_version" varchar,
    "cluster_id" bigint not null,
    "name" varchar not null,
    "uid" varchar unique,
    "namespace" varchar not null,
    "resource_version" varchar,
    "generation" integer,
    "owner_refs" varchar,
    "managed_fields" varchar,
    "rules" varchar,
    "labels" varchar,
    "annotations" varchar,
    "yaml" varchar,
    "is_deleted" boolean default false,
    "k8s_created_at" varchar,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "deleted_at" timestamptz,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- role_bindings table
create table if not exists "role_bindings" (
    "id" bigserial primary key,
    "kind" varchar default 'RoleBinding',
    "api_version" varchar,
    "cluster_id" bigint not null,
    "name" varchar not null,
    "uid" varchar unique,
    "namespace" varchar not null,
    "resource_version" varchar,
    "generation" integer,
    "owner_refs" varchar,
    "managed_fields" varchar,
    "role_ref" varchar,
    "subjects" varchar,
    "labels" varchar,
    "annotations" varchar,
    "yaml" varchar,
    "is_deleted" boolean default false,
    "k8s_created_at" varchar,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "deleted_at" timestamptz,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- network_policies table
create table if not exists "network_policies" (
    "id" bigserial primary key,
    "kind" varchar default 'NetworkPolicy',
    "api_version" varchar,
    "cluster_id" bigint not null,
    "namespace" varchar not null,
    "name" varchar not null,
    "uid" varchar unique,
    "resource_version" varchar,
    "generation" integer,
    "pod_selector" varchar,
    "policy_types" varchar,
    "ingress_rules" varchar,
    "egress_rules" varchar,
    "owner_refs" varchar,
    "labels" varchar,
    "annotations" varchar,
    "managed_fields" varchar,
    "yaml" varchar,
    "is_deleted" boolean default false,
    "k8s_created_at" varchar,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "deleted_at" timestamptz,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- Create indexes for network_policies
create index if not exists "idx_network_policies_cluster_namespace" on "network_policies"("cluster_id", "namespace");
create index if not exists "idx_network_policies_uid" on "network_policies"("uid");

-- validating_admission_policies table
create table if not exists "validating_admission_policies" (
    "id" bigserial primary key,
    "kind" varchar default 'ValidatingAdmissionPolicy',
    "api_version" varchar,
    "cluster_id" bigint not null,
    "name" varchar not null,
    "uid" varchar unique,
    "namespace" varchar,
    "resource_version" varchar,
    "generation" integer,
    "owner_refs" varchar,
    "managed_fields" varchar,
    "failure_policy" varchar,
    "match_constraints" varchar,
    "validations" varchar,
    "param_kind" varchar,
    "audit_annotations" varchar,
    "match_conditions" varchar,
    "variables" varchar,
    "conditions" varchar,
    "type_checking" varchar,
    "labels" varchar,
    "annotations" varchar,
    "yaml" varchar,
    "is_deleted" boolean default false,
    "k8s_created_at" varchar,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "deleted_at" timestamptz,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- validating_admission_policy_bindings table
create table if not exists "validating_admission_policy_bindings" (
    "id" bigserial primary key,
    "kind" varchar default 'ValidatingAdmissionPolicyBinding',
    "api_version" varchar,
    "cluster_id" bigint not null,
    "name" varchar not null,
    "uid" varchar unique,
    "namespace" varchar,
    "resource_version" varchar,
    "generation" integer,
    "owner_refs" varchar,
    "managed_fields" varchar,
    "policy_name" varchar,
    "param_ref" varchar,
    "match_resources" varchar,
    "validation_actions" varchar,
    "labels" varchar,
    "annotations" varchar,
    "yaml" varchar,
    "is_deleted" boolean default false,
    "k8s_created_at" varchar,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "deleted_at" timestamptz,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- validating_webhook_configurations table
create table if not exists "validating_webhook_configurations" (
    "id" bigserial primary key,
    "kind" varchar default 'ValidatingWebhookConfiguration',
    "api_version" varchar,
    "cluster_id" bigint not null,
    "name" varchar not null,
    "uid" varchar unique,
    "namespace" varchar,
    "resource_version" varchar,
    "generation" integer,
    "owner_refs" varchar,
    "managed_fields" varchar,
    "webhooks" varchar,
    "labels" varchar,
    "annotations" varchar,
    "yaml" varchar,
    "is_deleted" boolean default false,
    "k8s_created_at" varchar,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "deleted_at" timestamptz,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- mutating_webhook_configurations table
create table if not exists "mutating_webhook_configurations" (
    "id" bigserial primary key,
    "kind" varchar default 'MutatingWebhookConfiguration',
    "api_version" varchar,
    "cluster_id" bigint not null,
    "name" varchar not null,
    "uid" varchar unique,
    "namespace" varchar,
    "resource_version" varchar,
    "generation" integer,
    "owner_refs" varchar,
    "managed_fields" varchar,
    "webhooks" varchar,
    "labels" varchar,
    "annotations" varchar,
    "yaml" varchar,
    "is_deleted" boolean default false,
    "k8s_created_at" varchar,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "deleted_at" timestamptz,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- custom_resource_definitions table
create table if not exists "custom_resource_definitions" (
    "id" bigserial primary key,
    "kind" varchar default 'CustomResourceDefinition',
    "api_version" varchar,
    "cluster_id" bigint not null,
    "name" varchar not null,
    "uid" varchar unique,
    "namespace" varchar,
    "resource_version" varchar,
    "generation" integer,
    "owner_refs" varchar,
    "managed_fields" varchar,
    "group_name" varchar,
    "versions" varchar,
    "scope" varchar,
    "names" varchar,
    "conditions" varchar,
    "labels" varchar,
    "annotations" varchar,
    "yaml" varchar,
    "is_deleted" boolean default false,
    "k8s_created_at" varchar,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "deleted_at" timestamptz,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- priority_classes table
create table if not exists "priority_classes" (
    "id" bigserial primary key,
    "kind" varchar default 'PriorityClass',
    "api_version" varchar,
    "cluster_id" bigint not null,
    "name" varchar not null,
    "uid" varchar unique,
    "namespace" varchar,
    "resource_version" varchar,
    "generation" integer,
    "owner_refs" varchar,
    "managed_fields" varchar,
    "priority_value" integer,
    "global_default" boolean,
    "description" varchar,
    "preemption_policy" varchar,
    "labels" varchar,
    "annotations" varchar,
    "yaml" varchar,
    "is_deleted" boolean default false,
    "k8s_created_at" varchar,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "deleted_at" timestamptz,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- priority_level_configurations table
create table if not exists "priority_level_configurations" (
    "id" bigserial primary key,
    "kind" varchar default 'PriorityLevelConfiguration',
    "api_version" varchar,
    "cluster_id" bigint not null,
    "name" varchar not null,
    "uid" varchar unique,
    "namespace" varchar,
    "resource_version" varchar,
    "generation" integer,
    "owner_refs" varchar,
    "managed_fields" varchar,
    "limited" varchar,
    "nominal_concurrency_shares" integer,
    "queues" integer,
    "hand_size" integer,
    "queue_length_limit" integer,
    "conditions" varchar,
    "labels" varchar,
    "annotations" varchar,
    "yaml" varchar,
    "is_deleted" boolean default false,
    "k8s_created_at" varchar,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "deleted_at" timestamptz,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- csi_drivers table
create table if not exists "csi_drivers" (
    "id" bigserial primary key,
    "kind" varchar default 'CSIDriver',
    "api_version" varchar,
    "cluster_id" bigint not null,
    "name" varchar not null,
    "uid" varchar unique,
    "namespace" varchar,
    "resource_version" varchar,
    "generation" integer,
    "owner_refs" varchar,
    "managed_fields" varchar,
    "attach_required" boolean,
    "pod_info_on_mount" boolean,
    "storage_capacity" boolean,
    "token_requests" varchar,
    "requires_republish" boolean,
    "volume_lifecycle_modes" varchar,
    "labels" varchar,
    "annotations" varchar,
    "yaml" varchar,
    "is_deleted" boolean default false,
    "k8s_created_at" varchar,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "deleted_at" timestamptz,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- csi_nodes table
create table if not exists "csi_nodes" (
    "id" bigserial primary key,
    "kind" varchar default 'CSINode',
    "api_version" varchar,
    "cluster_id" bigint not null,
    "name" varchar not null,
    "uid" varchar unique,
    "namespace" varchar,
    "resource_version" varchar,
    "generation" integer,
    "owner_refs" varchar,
    "managed_fields" varchar,
    "drivers" varchar,
    "labels" varchar,
    "annotations" varchar,
    "yaml" varchar,
    "is_deleted" boolean default false,
    "k8s_created_at" varchar,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "deleted_at" timestamptz,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- ingress_classes table
create table if not exists "ingress_classes" (
    "id" bigserial primary key,
    "kind" varchar default 'IngressClass',
    "api_version" varchar,
    "cluster_id" bigint not null,
    "name" varchar not null,
    "uid" varchar unique,
    "namespace" varchar,
    "resource_version" varchar,
    "generation" integer,
    "owner_refs" varchar,
    "managed_fields" varchar,
    "controller" varchar,
    "parameters" varchar,
    "labels" varchar,
    "annotations" varchar,
    "yaml" varchar,
    "is_deleted" boolean default false,
    "k8s_created_at" varchar,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "deleted_at" timestamptz,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- certificate_signing_requests table
create table if not exists "certificate_signing_requests" (
    "id" bigserial primary key,
    "kind" varchar default 'CertificateSigningRequest',
    "api_version" varchar,
    "cluster_id" bigint not null,
    "name" varchar not null,
    "uid" varchar unique,
    "namespace" varchar,
    "resource_version" varchar,
    "generation" integer,
    "owner_refs" varchar,
    "managed_fields" varchar,
    "request" varchar,
    "signer_name" varchar,
    "usages" varchar,
    "conditions" varchar,
    "certificate" varchar,
    "labels" varchar,
    "annotations" varchar,
    "yaml" varchar,
    "is_deleted" boolean default false,
    "k8s_created_at" varchar,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "deleted_at" timestamptz,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- ip_addresses table
create table if not exists "ip_addresses" (
    "id" bigserial primary key,
    "kind" varchar default 'IPAddress',
    "api_version" varchar,
    "cluster_id" bigint not null,
    "name" varchar not null,
    "uid" varchar unique,
    "namespace" varchar,
    "resource_version" varchar,
    "generation" integer,
    "owner_refs" varchar,
    "managed_fields" varchar,
    "parent_ref" varchar,
    "labels" varchar,
    "annotations" varchar,
    "yaml" varchar,
    "is_deleted" boolean default false,
    "k8s_created_at" varchar,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "deleted_at" timestamptz,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- volume_attachments table
create table if not exists "volume_attachments" (
    "id" bigserial primary key,
    "kind" varchar default 'VolumeAttachment',
    "api_version" varchar,
    "cluster_id" bigint not null,
    "name" varchar not null,
    "uid" varchar unique,
    "namespace" varchar,
    "resource_version" varchar,
    "generation" integer,
    "owner_refs" varchar,
    "managed_fields" varchar,
    "attacher" varchar,
    "persistent_volume_name" varchar,
    "node_name" varchar,
    "source" varchar,
    "attached" boolean,
    "attachment_metadata" varchar,
    "detach_error" varchar,
    "attach_error" varchar,
    "labels" varchar,
    "annotations" varchar,
    "yaml" varchar,
    "is_deleted" boolean default false,
    "k8s_created_at" varchar,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "deleted_at" timestamptz,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- applications table
create table if not exists "applications" (
    "id" bigserial primary key,
    "kind" varchar default 'Application',
    "api_version" varchar,
    "cluster_id" bigint not null,
    "name" varchar not null,
    "uid" varchar unique,
    "namespace" varchar not null,
    "resource_version" varchar,
    "generation" integer,
    "owner_refs" varchar,
    "managed_fields" varchar,
    "descriptor" varchar,
    "component_kinds" varchar,
    "selector" varchar,
    "info" varchar,
    "assembly_phase" varchar,
    "conditions" varchar,
    "labels" varchar,
    "annotations" varchar,
    "yaml" varchar,
    "is_deleted" boolean default false,
    "k8s_created_at" varchar,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "deleted_at" timestamptz,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- replication_controllers table
create table if not exists "replication_controllers" (
    "id" bigserial primary key,
    "kind" varchar default 'ReplicationController',
    "api_version" varchar,
    "cluster_id" bigint not null,
    "name" varchar not null,
    "uid" varchar unique,
    "namespace" varchar not null,
    "resource_version" varchar,
    "generation" integer,
    "owner_refs" varchar,
    "managed_fields" varchar,
    "desired_replicas" integer,
    "current_replicas" integer,
    "ready_replicas" integer,
    "available_replicas" integer,
    "fully_labeled_replicas" integer,
    "observed_generation" integer,
    "selector" varchar,
    "template" varchar,
    "conditions" varchar,
    "labels" varchar,
    "annotations" varchar,
    "yaml" varchar,
    "is_deleted" boolean default false,
    "k8s_created_at" varchar,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "deleted_at" timestamptz,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- cron_jobs table (legacy, if needed)
create table if not exists "cron_jobs" (
    "id" bigserial primary key,
    "kind" varchar default 'CronJob',
    "api_version" varchar,
    "cluster_id" bigint not null,
    "namespace" varchar not null,
    "name" varchar not null,
    "uid" varchar unique,
    "resource_version" varchar,
    "schedule" varchar,
    "concurrency_policy" varchar,
    "suspend" boolean,
    "last_schedule_time" timestamptz,
    "owner_refs" varchar,
    "labels" varchar,
    "annotations" varchar,
    "managed_fields" varchar,
    "yaml" varchar,
    "is_deleted" boolean,
    "created_at" timestamptz default current_timestamp,
    "updated_at" timestamptz,
    "deleted_at" timestamptz,
    foreign key ("cluster_id") references "clusters"("id") on delete cascade
);

-- Create indexes for cron_jobs
create index if not exists "idx_cron_jobs_cluster_namespace" on "cron_jobs"("cluster_id", "namespace");
create index if not exists "idx_cron_jobs_uid" on "cron_jobs"("uid");

-- role_name_filters table
create table if not exists "role_name_filters" (
    "id" bigserial primary key,
    "role_name" varchar(100) not null,
    "cluster_uid" varchar(512) default '*',
    "ns_pattern" varchar(512) default '*',
    "resource_kind" varchar(200) not null,
    "name_pattern" varchar(512) default '*' not null,
    "created_at" timestamptz default current_timestamp
);

-- Create indexes for role_name_filters
create index if not exists "idx_rnf_role" on "role_name_filters"("role_name");
create index if not exists "idx_rnf_kind" on "role_name_filters"("resource_kind");