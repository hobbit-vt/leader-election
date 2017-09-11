# Example

```scala
val promoter = new ElectionPromoter(dealer, "uuid_of_instance", 10.seconds)
promoter.start()
...
if (promoter.isLeader || !promoter.isLeaderExist) {
  // do leader stuff here
}
```