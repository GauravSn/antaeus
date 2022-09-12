## Antaeus

Antaeus (/ænˈtiːəs/), in Greek mythology, a giant of Libya, the son of the sea god Poseidon and the Earth goddess Gaia. He compelled all strangers who were passing through the country to wrestle with him. Whenever Antaeus touched the Earth (his mother), his strength was renewed, so that even if thrown to the ground, he was invincible. Heracles, in combat with him, discovered the source of his strength and, lifting him up from Earth, crushed him to death.

Welcome to our challenge.

## The challenge

As most "Software as a Service" (SaaS) companies, Pleo needs to charge a subscription fee every month. Our database contains a few invoices for the different markets in which we operate. Your task is to build the logic that will schedule payment of those invoices on the first of the month. While this may seem simple, there is space for some decisions to be taken and you will be expected to justify them.

## Instructions

Fork this repo with your solution. Ideally, we'd like to see your progression through commits, and don't forget to update the README.md to explain your thought process.

Please let us know how long the challenge takes you. We're not looking for how speedy or lengthy you are. It's just really to give us a clearer idea of what you've produced in the time you decided to take. Feel free to go as big or as small as you want.

## Developing

Requirements:
- \>= Java 11 environment

Open the project using your favorite text editor. If you are using IntelliJ, you can open the `build.gradle.kts` file and it is gonna setup the project in the IDE for you.

### Building

```
./gradlew build
```

### Running

There are 2 options for running Anteus. You either need libsqlite3 or docker. Docker is easier but requires some docker knowledge. We do recommend docker though.

*Running Natively*

Native java with sqlite (requires libsqlite3):

If you use homebrew on MacOS `brew install sqlite`.

```
./gradlew run
```

*Running through docker*

Install docker for your platform

```
docker build -t antaeus
docker run antaeus
```

### App Structure
The code given is structured as follows. Feel free however to modify the structure to fit your needs.
```
├── buildSrc
|  | gradle build scripts and project wide dependency declarations
|  └ src/main/kotlin/utils.kt 
|      Dependencies
|
├── pleo-antaeus-app
|       main() & initialization
|
├── pleo-antaeus-core
|       This is probably where you will introduce most of your new code.
|       Pay attention to the PaymentProvider and BillingService class.
|
├── pleo-antaeus-data
|       Module interfacing with the database. Contains the database 
|       models, mappings and access layer.
|
├── pleo-antaeus-models
|       Definition of the Internal and API models used throughout the
|       application.
|
└── pleo-antaeus-rest
        Entry point for HTTP REST API. This is where the routes are defined.
```

### Main Libraries and dependencies
* [Exposed](https://github.com/JetBrains/Exposed) - DSL for type-safe SQL
* [Javalin](https://javalin.io/) - Simple web framework (for REST)
* [kotlin-logging](https://github.com/MicroUtils/kotlin-logging) - Simple logging framework for Kotlin
* [JUnit 5](https://junit.org/junit5/) - Testing framework
* [Mockk](https://mockk.io/) - Mocking library
* [Sqlite3](https://sqlite.org/index.html) - Database storage engine

Happy hacking 😁!

---

### Name - Gaurav Saini
### Date - 2022-09-11

## Assumptions
- I have assumed that the payment service call is a blocking operation.
- Invoices which failed due to `CutomerNotFoundException` are invalid.
- Invoices which failed due to `CurrencyMismatchedException` has generation issues.
- Above errors must be handled by a system which can resolve and fix the invoice. I assume that we might have a billing case management system.
- For invoices which failed due to `NetworkException`, I have assumed that this issue might be temporary; hence I have added a retry on payment. But if it still fails, I have sent a mocked alert to the DevOps team to notify them about the payment service failures.
- Another assumption is that if we process any invoice, we might need to notify the respective customer about their subscription status.
- Also, if something goes wrong in external system invocation, we need an alerting mechanism to inform the respective technical team.

## Process
- Since we need to schedule pending invoices billing on the first day of the month, I thought of using a cron expression-based scheduler, but Kotlin does not support cron expression natively, so I used Quartz job scheduler library to achieve the same. I created a `BiilingJob` which calls `BillingService#processInvoices()` to initiate pending invoices billing on the first day of every month(`0 0 0 1 * ?`).
- Since I assumed that payment service calls were blocking operations, I called pending invoice payments in separate threads using a fixed thread pool to process them concurrently.
- Once we have all the pending invoices to process, we need to map different actions based on the payment provider's response -
  - If payment is made successfully (payment service returns `true`), then we update invoice status to `PAID` and send customer notification about the same.
  - If payment failed due to insufficient funds (payment service returns `false`), then we set invoice status to `UNPAID` and send customer notification about the same.
  - If payment failed due to `CustomerNotFoundException`, we set invoice status to `FAILED` and send the billing case event to the external case management system with the category `INVALID_CUSTOMER`.
  - If payment failed due to `CurrencyMismatchException`, then we set invoice status to `FAILED` and send the billing case event to the external case management system with category `CURRENCY_MISMATCH`.
  - If payment failed due to `NetworkException`, we retry three times with a 1-second delay. Even after it dies, send mocked alert to the DevOps team.


## Few other thoughts
- **Idempotency** - It is possible that the invoice payment was a success, but we could not get the response in time and receive `NetworkException`. In that case, we retry and perform double payments for the same invoice; this is a classic issue and can be solved using idempotency flow, where we send some unique identifier with the payment request as request key for the same invoice. The payment service hard checks the request key to check whether we are trying to process the same invoice again or not, if it is same request we get the same response as the first request else it is considered as new request.
- **Rate Limiting** - Usually, payment providers keep some rate limiting to safeguard against DDoS attacks and allow RPS threshold for a client. We might add client-side rate limiting to respect that threshold to prevent request drops.
- **Scalability** - If we have to process millions of invoices monthly, we might need to scale and add multiple instances of this service. Still, there is one problem, if we keep the same logic to schedule pending invoices billing, we might process the same invoices numerous times since we would be fetching duplicate invoices in each instance to process. One way to solve this problem is to provide invoices for processing to each instance through a messaging queue like Kafka, where each member would be part of the same consumer group and receive different invoices to process and increase parallel processing.

## Challenge Time
It took almost 6 hours spanning three days to complete this challenge.