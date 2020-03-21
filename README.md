# crx77

Some rule validator/manager

## Installation
```
lein deps
```
## Usage
```
lein run
```
## Explanation
### parse.clj
All 'read-parse-init' stuff. Also some initial validations.
### db.clj
Crux stuff + main logic for rules reachability and processing order

**What is goal=true** - goal rules are those that we need to calculate at the end. Here S1 - is goal rule
```
S1 = K1, K2, K3
```
**What is shadow** - shadow is alternative way to reach goal. 
```
S1 = K1, K2 | K3
```
As result 2 new edges will be created:
* {id:__S1  deps:K2 }
* {id:__S1_1  deps:K3   shadow_of: __S1}

And rule will look like
```
S1 = K1, __S1
```
### Steps
please go to **db.clj** -> **startDBStuff**
* *checkRulesReachability* - take all goal rules, recursively run through deps and check that path has only unique **goal** deps
* *processShadows* - check edges that has shadows. If edge is valid=false, try to replace it with valid shadow
* *checkRulesReachability* again, since previous step could change the world
* at this moment we have list of invalid rules
* *checkForMultitasking*, run 1 - take all **non-goal** edges and using reverse search find edges that depends on this edge. If vector has 2+ items - mark those goal rules as non-aync
* *checkForMultitasking*, run 1 - take all **goal** and using same mechanism mark rules which depends on this goal task as non-async
* now we have list of async goals
* to calculate order of execution there could be different ways, I was thinking about 2 of them:
    * search for the most 'valuable'(those that has biggest number of refs on it ) dependencies and calculate them first and etc.
    * recursive search of deps and execution in opposite order
I choosed 2nd option. See example:
```
S1=K1,S2
S2=K2,S3
S3=S4
S4=K1,K5
```
**Notice** code will not display resources that were already calculate, so output will be following
```
S4=K1,K5
S3=
S2=K2
S1=
```