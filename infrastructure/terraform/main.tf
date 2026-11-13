terraform {
  required_version = ">= 1.9, < 2.0"
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 6.0" }
  }
}
provider "aws" {
  region = var.region
  default_tags { tags = { Project = "FleetOps", Environment = var.environment, ManagedBy = "Terraform" } }
}
variable "region" {
  type    = string
  default = "us-east-1"
}
variable "environment" {
  type    = string
  default = "portfolio"
  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{2,20}$", var.environment))
    error_message = "Use 3–21 lowercase letters, numbers or hyphens."
  }
}
variable "deletion_protection" {
  type    = bool
  default = true
}
resource "aws_dynamodb_table" "fleetops" {
  name                        = "fleetops-${var.environment}"
  billing_mode                = "PAY_PER_REQUEST"
  hash_key                    = "pk"
  range_key                   = "sk"
  deletion_protection_enabled = var.deletion_protection
  attribute {
    name = "pk"
    type = "S"
  }
  attribute {
    name = "sk"
    type = "S"
  }
  point_in_time_recovery { enabled = true }
  server_side_encryption { enabled = true }
}
resource "aws_ecr_repository" "backend" {
  name                 = "fleetops-${var.environment}-api"
  image_tag_mutability = "IMMUTABLE"
  force_delete         = false
  image_scanning_configuration { scan_on_push = true }
  encryption_configuration { encryption_type = "AES256" }
}
resource "aws_cloudwatch_log_group" "backend" {
  name              = "/ecs/fleetops-${var.environment}"
  retention_in_days = 14
}
resource "aws_iam_role" "task" {
  name               = "fleetops-${var.environment}-task"
  assume_role_policy = jsonencode({ Version = "2012-10-17", Statement = [{ Effect = "Allow", Principal = { Service = "ecs-tasks.amazonaws.com" }, Action = "sts:AssumeRole" }] })
}
resource "aws_iam_role_policy" "database" {
  role   = aws_iam_role.task.id
  policy = jsonencode({ Version = "2012-10-17", Statement = [{ Effect = "Allow", Action = ["dynamodb:DescribeTable", "dynamodb:GetItem", "dynamodb:Query", "dynamodb:PutItem", "dynamodb:ConditionCheckItem"], Resource = aws_dynamodb_table.fleetops.arn }] })
}
output "table_name" { value = aws_dynamodb_table.fleetops.name }
output "repository_url" { value = aws_ecr_repository.backend.repository_url }
output "task_role_arn" { value = aws_iam_role.task.arn }
output "log_group_name" { value = aws_cloudwatch_log_group.backend.name }
