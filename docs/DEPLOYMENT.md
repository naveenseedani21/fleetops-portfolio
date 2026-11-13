# Hosting readiness and cost plan

**Owner preference:** keep development in GitHub with meaningful commits. Host only after the build is reviewed and approved. This project has not been deployed. The Terraform directory currently defines a foundation, not the complete ECS/S3/CloudFront application.

Target: a single ECS Fargate API task; DynamoDB on-demand; S3 private bucket and CloudFront for static Angular; HTTPS ALB; CloudWatch logs with 14-day retention; production OIDC issuer. A public Fargate subnet with only ALB-originated inbound traffic can avoid NAT gateway charges. Public IPs incur charges. Production identity, TLS certificate, domain, frontend callback URL and origin routing must be chosen before infrastructure is finalized. Do not deploy the development Keycloak realm.

Indicative US East monthly baseline at 730 hours, before taxes/free-tier credits: a 0.5-vCPU / 1-GB x86 task is about $18.02 using $0.04048/vCPU-hour and $0.004445/GB-hour; ALB base is about $16.43 plus LCU charges. Public IPv4 is $3.65/address-month, so a task and two ALB addresses add about $10.95. That makes an illustrative baseline roughly $45/month before LCU, bandwidth, identity, storage and logs; actual allocated IPs and workload change it. These are planning calculations, not a quote. Recheck [Fargate pricing](https://aws.amazon.com/fargate/pricing/), [load balancer pricing](https://aws.amazon.com/elasticloadbalancing/pricing/) and [VPC pricing](https://aws.amazon.com/vpc/pricing/) before approval.

A NAT gateway at the example $0.045/hour adds $32.85/month plus data processing and its public IP. Do not provision one by default. DynamoDB on-demand charges per request and storage; point-in-time recovery adds cost. CloudWatch charges ingestion and retained storage; retention bounds duration, not volume. S3/CloudFront charges storage, requests and delivery; inspect free-tier eligibility rather than assuming it. Log budgets and alarms are still to be implemented.

Lower-cost demo options: operate a scheduled/time-limited Fargate deployment, keep the app local with a recorded demo, or evaluate a single small VM (more patching responsibility and a departure from the ECS objective). Scaling the task to zero does not stop ALB/IP/storage charges. Avoid a public writable anonymous demo until abuse controls and data reset are designed.

Before hosting:
- Finish and pass Docker/real browser CI, dependency review and accessibility checks.
- Finish organization administration, reporting and intended MVP acceptance criteria.
- Configure production OIDC tenant claims, short-lived tokens, HTTPS and CSP.
- Complete ECS/network/frontend Terraform, least-privilege execution/deployment roles, alarms and cost budget.
- Add GitHub Actions OIDC trust restricted to this repository's protected production environment. Require an environment reviewer; never use long-lived AWS keys.
- Build/push immutable image digests, approve an explicit Terraform plan, deploy, verify health and document rollback.

No deployment workflow is currently enabled. Infrastructure changes require an explicit owner approval; local development and GitHub CI do not deploy AWS resources.
