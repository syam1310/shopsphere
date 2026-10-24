# ShopSphere review fixes

## Changes made

1. **Uncertain inventory reservation handling**
   - Order creation tracks a reservation attempt before calling Inventory Service, so timeouts trigger compensation using the same reservation UUID.
   - Inventory release creates a `RELEASED` tombstone when the reservation UUID is not yet present. Because reserve and release lock the inventory row before reading/creating the reservation, a late reserve sees the tombstone and cannot consume stock.
2. **Order database commit failure handling**
   - Added `OrderPersistenceService` as a separate transactional bean and uses `saveAndFlush`. Its transaction commits before returning to `OrderService`, allowing a persistence/commit failure to enter the compensation path.
   - Remote inventory calls are no longer made inside the order-creation database transaction.
3. **Tests**
   - Added tests for timeout compensation, order persistence failure compensation, release tombstones, and rejecting a late reservation after a release tombstone.
   - Removed noisy MVC mapping diagnostics from the validation test and disabled servlet filters in that validation-only test slice so a mocked filter cannot short-circuit requests.
   - Added explicit test properties for the required internal secret and JWT secret in the application context test.
4. **Credentials**
   - Removed `.env` from this review archive and added `.env.example` with placeholders only. Do not distribute real secrets in source archives.

## Validation status

The code and tests were updated, but automated test execution could not be completed in this environment because the Maven wrapper could not download Maven 3.9.16. Run the service test suites locally with Java 21 and Docker available. Do not treat this archive as production-verified until all tests pass and the database schema is checked.

## Recommended local verification

```bash
cd inventory-service && ./mvnw test
cd ../order-service && ./mvnw test
cd ../product-service && ./mvnw test
cd ../user-service && ./mvnw test
```
