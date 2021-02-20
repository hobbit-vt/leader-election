
# Example

```scala
val promoter = new ElectionPromoter(dealer, "uuid_of_instance", 10.seconds)
promoter.start()
...
if (promoter.isLeader || !promoter.isLeaderExist) {
  // do leader stuff here
}
```

#Release notes
0.2.0
- monix updated to 3.0.0-RC3. Note:`AsyncSemaphore` changed package and API comparing to 2.x 
- circe updated to 0.10.0

0.3.0
- scala updated to 2.12.13
- monix updated to 3.3.0 
- circe updated to 0.13.0
- async-http-client updated to 2.12.2
- scala-logging updated to 3.9.2
