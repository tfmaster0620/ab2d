# Required input vars
variable "env" {}
variable "allocated_storage_size" {}
variable "engine_version" {}
variable "instance_class" {}
variable "snapshot_id" {}
variable "subnet_group_name" {}
variable "parameter_group_name" {}
variable "iops" {}
variable "maintenance_window" {}
variable "copy_tags_to_snapshot" {}
variable "vpc_id" {}
variable "backup_retention_period" {}
variable "backup_window" {}
variable "kms_key_id" {}
variable "db_instance_subnet_ids" {type=list(string)}
variable "identifier" {}
variable "multi_az" {}
variable "username" {}
variable "password" {}
variable "skip_final_snapshot" {}
variable "cpm_backup" {}
