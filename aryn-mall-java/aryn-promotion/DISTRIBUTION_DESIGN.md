# Distribution Feature Design Document

## 1. Architecture Overview

The distribution (affiliate) module is part of  and follows the standard MyBatis-Plus service pattern.

### Module Structure



## 2. Core Business Flows

### 2.1 Distribution User Registration
- User registers as distributor, optionally with inviter (parent distributor)
- Validates inviter exists and is enabled
- Initializes commission fields to ZERO
- Updates inviter subordinate count

### 2.2 Order Settlement (Commission Calculation)
- Triggered by  via RocketMQ
- Idempotent: checks  unique index + DuplicateKeyException
- Level 1 commission: orderAmount * commissionRate -> distributor
- Level 2 commission: orderAmount * commissionRateLevel2 -> distributor inviter
- Optimistic lock (@Version) protects commission updates
- Records commission flow for audit trail

### 2.3 Refund Commission Reversal
- Triggered by  via RocketMQ
- Calculates refund commission proportionally: commissionAmount * (refundAmount / orderAmount)
- Deducts from available commission (floor at ZERO)
- Updates distribution order status to REFUNDED
- Records expense commission flow

### 2.4 Withdraw Application
- User applies for withdrawal
- Validates: user exists, user enabled, amount >= minWithdrawAmount, available >= amount
- Freezes commission: available -> frozen
- Creates withdraw record with generated withdrawNo (DW + datetime + random)

### 2.5 Withdraw Audit
- Admin approves: frozen -> withdrawn, records expense flow
- Admin rejects: frozen -> available (unfreeze)
- Sets audit time and auditor

### 2.6 Config Management
- Only one config can be active at a time
- enableConfig() disables all others before enabling target

## 3. API Endpoints

### Admin APIs
| Method | Path | Description |
|--------|------|-------------|
| GET | /distribution/config/page | Config pagination |
| GET | /distribution/config/{id} | Config detail |
| POST | /distribution/config | Create config |
| PUT | /distribution/config | Update config |
| PUT | /distribution/config/enable/{id} | Enable config |
| DELETE | /distribution/config/{id} | Delete config |
| GET | /distribution/user/page | User pagination |
| GET | /distribution/user/{id} | User detail |
| POST | /distribution/user/register | Register distributor |
| PUT | /distribution/user/enable/{userId} | Enable user |
| PUT | /distribution/user/disable/{userId} | Disable user |
| DELETE | /distribution/user/{id} | Delete user |
| GET | /distribution/order/page | Order pagination |
| GET | /distribution/order/{id} | Order detail |
| POST | /distribution/order/settle | Manual settle |
| GET | /distribution/withdraw/page | Withdraw pagination |
| GET | /distribution/withdraw/{id} | Withdraw detail |
| POST | /distribution/withdraw/audit | Audit withdraw |

### App APIs (Mobile)
| Method | Path | Description |
|--------|------|-------------|
| POST | /app/distribution/register | Register as distributor |
| GET | /app/distribution/config | Get active config |
| GET | /app/distribution/center | Distribution center summary |
| GET | /app/distribution/commission/page | Commission flow pagination |
| POST | /app/distribution/withdraw/apply | Apply for withdrawal |
| GET | /app/distribution/withdraw/page | Withdraw records pagination |

## 4. Database Tables

See  for DDL.

Key indexes:
- distribution_user: UNIQUE(user_id), INDEX(inviter_user_id)
- distribution_order: UNIQUE(biz_order_id), INDEX(distributor_user_id), INDEX(buyer_user_id)
- distribution_commission_flow: INDEX(user_id), INDEX(biz_order_id)
- distribution_withdraw: UNIQUE(withdraw_no), INDEX(user_id)

## 5. Concurrency & Consistency

- **Optimistic Lock**:  on DistributionUser and DistributionOrder
- **Idempotent Settlement**: UNIQUE index on biz_order_id + DuplicateKeyException catch
- **Transaction**: All write operations use @Transactional(rollbackFor = Exception.class)
- **Commission Freeze**: Withdraw moves available->frozen first, preventing over-withdrawal
