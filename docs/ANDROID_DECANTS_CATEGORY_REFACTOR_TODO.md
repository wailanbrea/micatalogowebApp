# Android Decants and Categories Refactor

## Baseline

- Android HEAD: `2104999aa743da4b8f20aa9cf7d1725ae6f04c79` (`main`).
- Backend HEAD: `ac659b0911119726aaf764d3387c6ca1da6691b6` (`master`).
- Both worktrees contain pre-existing local changes. They must not be reverted or mixed into unrelated patches.
- No QA products, sales, migrations, or production mutations have been created for this task.

## Confirmed Risks

- Android's Categories screen is local-only; it is not a remote CRUD contract or outbox operation.
- Android exposed Categories from CatalogHome and from the drawer; the duplicate is now removed and the drawer entry lives under Catálogo.
- Android Room categories have no `shop_id` and `CategoryDao.observeAll()` is global; this cannot be called multi-shop safe while the backend catalog uses shop-scoped categories.
- Decant `available_ml` currently represents source liquid but is not an explicit reserved pool.
- The working tree now adds nullable `reserved_decant_ml`; `NULL` remains legacy/ambiguous, while `0+` is an explicit pool.
- `openBottle()` increments opened bottles without reducing the sealed-bottle liquid represented by `available_ml`.
- Decant stock is persisted as a derived cache in `product_inventories.stock_quantity` and several readers use it as authority.
- Source and decant mutation lock/order and tenant validation are not centralized.
- Editing a referenced category/unit can silently reassign product form defaults.

## Decisions

- Do not reinterpret historical `available_ml` automatically. Existing ambiguous records require reconciliation/reporting.
- Add an explicit additive reserved-ml field only after proving the smallest compatible schema/API contract.
- Reservation is not a sale, invoice, cash movement, FIFO consumption, or profit event.
- A decant presentation reads from the source pool; presentations never own an independent stock pool.
- Source products reserved exclusively for decants must not appear as sellable bottle products.
- Keep server authorization authoritative for shop, role, capability, plan, and idempotency.
- Keep Categories and Units in the existing `CatalogSettingsScreen`; do not merge them with brands/attributes.
- Do not publish an APK or deploy backend without a separate explicit user instruction after QA.

## Work Plan

### Audit

- [x] Read current HEAD and local worktree state for both repositories.
- [x] Map Category/Unit navigation and local persistence.
- [x] Map decant source, available ml, opened bottles, FIFO, POS, orders, and mobile operations.
- [x] Add a regression fixture for 2×100 ml with only 1 bottle reserved/opened.
- [ ] Verify category/unit tenant isolation and effective permissions. Current finding: Room categories/units are not shop-scoped and have no remote CRUD/outbox contract.

### Categories and Units

- [x] Move the sole drawer entry under Catalog as `Categorías y unidades`.
- [x] Remove the permanent CatalogHome Categories button without removing useful actions.
- [x] Keep both existing tabs and CRUD behavior, but make the current local-only scope explicit in the audit.
- [ ] Add contextual create-category/unit return paths without losing product forms.
- [ ] Prevent silent reassignment when a category/unit is referenced or inactive.
- [ ] Add Compose, Room, permission, and multi-shop tests.

### Decant Domain

- [x] Define source sellable bottles, reserved ml, physical ml, and FIFO cost separately in the working inventory path.
- [x] Expose the reserved pool through the catalog/mobile API and Room sync with legacy fallback.
- [ ] Implement an additive, tenant-scoped reservation operation with stable idempotency UUID and payload conflict detection.
- [ ] Lock source and dependent inventories in a stable order and validate all products belong to the same shop.
- [ ] Derive presentation availability only from reserved ml after acknowledged synchronization.
- [ ] Add explicit source-only/sellable-as-bottle state if the current product model cannot express it safely.
- [ ] Protect POS, public catalog, orders, returns, valuation, and reports from selling reserved/exclusive source bottles.
- [ ] Preserve legacy records and expose ambiguous records for owner reconciliation instead of guessing.

### Android UX and Sync

- [ ] Replace four permanent Decants actions with one `+ Crear decants` flow and two source choices.
- [ ] Support new exclusive source and existing-source reservation flows.
- [ ] Show source image plus atomizer placeholder/overlay for decant presentations.
- [ ] Show sellable bottles, reserved ml, and presentations separately.
- [ ] Disable duplicate pending operations and show Pending/Synchronizing/Confirmed/Conflict states.
- [ ] Make remote presentation metadata read-only when server rules reject local edits.

### Tests and QA

- [ ] Add Laravel tests for reservation, idempotency, 409 payload conflicts, concurrency, capabilities, tenant isolation, and legacy data.
- [ ] Add Android unit/Compose/Room/outbox tests for calculations and state transitions.
- [ ] Run existing regression suites before any production migration.
- [ ] Use only a verified QA/sandbox shop belonging to `wailandkey`; if unavailable, mark real-device scenarios BLOCKED.
- [ ] Do not execute production sales or SQL deletion to clean up test data.
- [ ] Capture before/after evidence and report every test as PASS, FAIL, or BLOCKED.

## Current QA Blockers

- No verified `wailandkey` session or sandbox shop has been established in this audit.
- The emulator currently cannot be treated as evidence of the real account's shop data.
- Production backend data must not be mutated until the reservation contract and rollback/reconciliation path are implemented and explicitly authorized.
