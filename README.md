
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
0.20.0
- monix updated to 3.0.0-RC3. Note:`AsyncSemaphore` changed package and API comparing to 2.x 
- circe updated to 0.10.0
