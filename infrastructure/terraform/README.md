# AWS foundation — not a complete deployment
Defines DynamoDB, ECR, the future ECS task's database IAM role and a 14-day CloudWatch log group. It deliberately does not provision ECS, networking, an ALB or a public frontend yet. The owner wants to review the build before choosing hosting. Nothing has been applied.

```sh
terraform init
terraform fmt -check
terraform validate
terraform plan -out=review.tfplan
```

Planning accesses AWS APIs and requires credentials; validation does not create resources. Obtain owner approval of resources, region and current costs before `terraform apply review.tfplan`. Do not commit plans/state. Configure an encrypted, locked remote state backend before collaborative production changes.

For approved cleanup: back up data, explicitly disable table deletion protection through a reviewed change, empty ECR only after retaining required images, then review `terraform plan -destroy` before approving `terraform destroy`. No destroy operation runs automatically.
