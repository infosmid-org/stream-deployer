# Guidelines

grill me to ensure understanding of requests.

## Codebase intelligence

Before grepping or reading files to answer structural questions
(who calls X, what does Y depend on, class/function relationships),
first call the codegraphcontext mcp tools to add or query code structures, class hierarchies, callers 



## Knowledge
Leverage `javadoccentral` mcp to obtain library reference information of the correct version of the relevant libraries according to version catalog.

Leverage perplexity_ask, perplexity_research to obtain up-to-date information on apis and technologies, 
and perplexity_reason to obtain answers to difficult problems.
 

## Executing Builds and Tests

* SDKMAN is installed and Java 25 is configured with SDKMAN.
* Use ./gradlew to build and test the project

## Adhoc tests
* When using python, use pipx to install packages.

## Logging
* In Java code use Java Util Logging 
* 

## Java Coding Guidelines

* getter method for non-primitive types and collection types have not been initialized  should be annotated with `@Nullable` and  `org.jspecify.annotations.Nullable` import into the class file.
* properties annotated with `@Id` or `@NotNullable` or `@NotBlank` cannot be annotated as nullable


## Bash Coding Guidelines
* Prefer understandable code to concise code.

## Language

all documentation and comments should be written strictly in **Simplified Technical English (ASD-STE100)**

## Commits

* Use conventions as described in https://www.conventionalcommits.org/en/v1.0.0

## Important
Do not remove settings from `.env` files.
Do not add `.env` file to git

