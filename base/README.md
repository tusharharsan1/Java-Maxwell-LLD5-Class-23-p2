# The Guarded Payout at ShopNest

## Problem Statement
ShopNest sells things for many sellers. Every week the shop pays each seller their money. We call this a **payout**. The money goes through a bank, and the bank is a "friend" of our shop: we call it over the network.

A payout is not a simple yes or no. It moves through steps. It is saved first. Then the bank takes it. Later the bank confirms it, or somebody cancels it. And sometimes the bank says no, or does not answer at all. A shop that moves money must be strict about this. A payout that is PAID must never go back to SENT. A bank that says no must give the seller's payout a clear FAILED record, and the caller a clear answer. In this problem you make the payout service strict.

## Requirements
### What the application does
| Request | Answer | Written already? |
|---|---|---|
| `POST /payouts` with the body `{"sellerId":"S-1","amount":49900}` | the payout, as JSON: `{"id":"...","sellerId":"S-1","amount":49900,"status":"SENT","bankReference":"BANK-1","failureReason":null}` | partly |
| `GET /payouts` | all the payouts, as a JSON list (in no special order) | yes |
| `GET /payouts/{id}` | the payout, or `404` | yes |
| `POST /payouts/{id}/confirm` | the bank has confirmed the transfer: the payout moves to PAID | yes (it uses your code) |
| `POST /payouts/{id}/cancel` | the payout is cancelled: it moves to CANCELLED | yes (it uses your code) |
| `GET /pretend/bank?mode=ok` (or `no`, or `silent`) | changes the mood of the pretend bank | yes |

The amount is a whole number of **paise** (49900 means 499.00 rupees) and at least 100. The check of the body (`amount` at least 100, `sellerId` not empty) is already written.

### The steps of a payout (Tasks 1 and 2)
```
REQUESTED ---> SENT ---> PAID
    |           |
    v           v
  FAILED     CANCELLED
```
| From | It may move to |
|---|---|
| `REQUESTED` (saved, the bank has not been asked yet, or has not answered yet) | `SENT` (the bank took it) or `FAILED` (the bank said no, or did not answer) |
| `SENT` | `PAID` (the bank confirmed it) or `CANCELLED` |
| `PAID`, `CANCELLED`, `FAILED` | nowhere: they are final |

Every other move is refused. The status has **no setter**: it can change only through one method, `Payout.moveTo`, and that method asks the status if the move is allowed.

### The answers (Tasks 3 and 4)
| When | The shop answers |
|---|---|
| `POST /payouts`, and the bank takes it | `201`, and the payout with `"status":"SENT"` and the `bankReference` of the bank (for example `BANK-1`) |
| `POST /payouts`, and the bank says no | `502`, with the content type `application/problem+json`, the title `The bank could not take the payout`, and the words of the bank in `detail`. The payout is saved with `"status":"FAILED"` and the same words in `failureReason` |
| `POST /payouts`, and the bank does not answer (`silent`) | the same `502`, with the words of the exception in `detail` (they say that the bank did not answer). The payout is saved as `FAILED` |
| `confirm` of a `SENT` payout | `200`, and `"status":"PAID"` |
| `cancel` of a `SENT` payout | `200`, and `"status":"CANCELLED"` |
| any move that is not on an arrow (for example `confirm` of a payout that is already `PAID`, or `cancel` of a `FAILED` one) | `409`, with the content type `application/problem+json` and the title `Not allowed for this payout`. The payout does not change |
| a payout that does not exist | `404` (this is already written, and it is your example) |
| a body that is not valid (for example `amount` 99) | `400` (this is already written). No payout is saved, and the bank is not called |

### The rules
- **Money is a whole number of paise**, as a `long`. Never a decimal.
- **One door for the status.** Do not add `setStatus`. Every change of status goes through `moveTo`, and `moveTo` must refuse a move that is not allowed.
- **No answer means: we don't know.** When the bank does not answer, we do not know if it sent the money. For now we save FAILED and say `502`. But we never call the bank a second time by ourselves for the same request, because a second call could pay the seller twice. The bank gets **one call** for each `POST /payouts`.
- **Catch only `GatewayException`** in `create`, not `Exception` or `RuntimeException`.
- **Do not put `@Transactional` on `create`.** No database transaction may stay open while we wait for the bank. The saves are short steps, and the call to the bank is between them.
- The pretend bank writes one line in the console for every call it gets, so you can count the calls that really reach it.

To achieve these requirements we need to do 4 things:
- **Task 1:** write the rules of the steps in `PayoutStatus.canMoveTo`.
- **Task 2:** make `Payout.moveTo` refuse a move that is not allowed.
- **Task 3:** finish `PayoutService.create`: call the bank, move the payout to SENT, and handle a bank that says no or does not answer.
- **Task 4:** add the two handlers to `ApiExceptionHandler`: the clear `502`, and the clear `409`.

## Instructions
1. There is one TODO note in each of these four files: `PayoutStatus.java` (`TODO 1`), `Payout.java` (`TODO 2`), `PayoutService.java` (`TODO 3`) and `ApiExceptionHandler.java` (`TODO 4`).
2. Do not modify the other files (they are finished), the dependencies in `pom.xml`, the names of the classes and methods, or the dependencies that are injected in the classes. Feel free to add new methods or imports if required.
3. Start the application (`mvn spring-boot:run`, or run `PayoutApplication` in your IDE). It runs on port 8080 (if that port is busy, stop the other application that uses it, for example the project from class). The database is H2, and it lives in memory, so it starts empty each time. Then try these commands in a second terminal, and compare with the answers below. First a small helper, so the commands are short:
   ```
   $ pay() { curl -s -i -X POST localhost:8080/payouts -H "Content-Type: application/json" -d "$1"; }
   ```
   When all four tasks are finished, you should see this (the `id` is different on your laptop, and `curl -i` also shows other header lines that are not written here):
   ```
   $ pay '{"sellerId":"S-1","amount":49900}'
   HTTP/1.1 201
   ...
   {"id":"2bb889e3-d34a-4f05-b656-5525d8f00ebe","sellerId":"S-1","amount":49900,"status":"SENT","bankReference":"BANK-1","failureReason":null}

   $ curl -s -i -X POST localhost:8080/payouts/<the id>/confirm
   HTTP/1.1 200
   ...
   {"id":"2bb889e3-...","sellerId":"S-1","amount":49900,"status":"PAID","bankReference":"BANK-1","failureReason":null}

   $ curl -s -i -X POST localhost:8080/payouts/<the id>/confirm
   HTTP/1.1 409
   Content-Type: application/problem+json
   ...
   {"detail":"A payout cannot move from PAID to PAID","instance":"/payouts/2bb889e3-.../confirm","status":409,"title":"Not allowed for this payout"}
   ```
   `PAID` is final, so a second confirm, and a cancel, are refused with `409`. Now the bank says no, and then does not answer:
   ```
   $ curl "localhost:8080/pretend/bank?mode=no"
   bank is now: no

   $ pay '{"sellerId":"S-2","amount":20000}'
   HTTP/1.1 502
   Content-Type: application/problem+json
   ...
   {"detail":"Bank answered 400: the shop account has too little money","instance":"/payouts","status":502,"title":"The bank could not take the payout"}

   $ curl "localhost:8080/pretend/bank?mode=silent"
   bank is now: silent

   $ pay '{"sellerId":"S-3","amount":20000}'
   HTTP/1.1 502
   ...
   {"detail":"Bank did not answer: read timed out","instance":"/payouts","status":502,"title":"The bank could not take the payout"}

   $ curl "localhost:8080/pretend/bank?mode=ok"
   bank is now: ok
   ```
   Look at the console of your application too. The pretend bank wrote **1 line** for each of these `POST /payouts` (`pretend bank: S-2, 20000 paise -> no`). It was called once, and never again. The two failed payouts are saved. Look at them with `curl -s localhost:8080/payouts`: you should see `"status":"FAILED"` and the words of the bank in `failureReason` for both.

   Before you start, the application already runs, but the payout service is not strict yet: `POST /payouts` saves the payout as `REQUESTED` and never calls the bank (the console stays empty, also when the bank is on `no`). And any move is accepted: a `confirm`, then a `cancel` of the same payout, both give `200`, and the payout goes from `REQUESTED` to `PAID` to `CANCELLED`.
4. Your code will be checked with the test file `src/test/java/com/shopnest/payout/PayoutGuardTest.java`. It checks the cases in the Requirements above, and the rules. While you write your code, use the commands in instruction 3 to check your work.
5. You do not need to install a database, and you do not need the internet or a key. It works with Java 17 and Maven, like the project from class.
6. Hints (all of these use ideas from class):
   - **Task 1 (the rules).** A `switch` on `this` that gives back a boolean. Write one case for each status that has a way out. All the final statuses can share the `default` case:
     ```java
     return switch (this) {
         case REQUESTED -> next == SENT || next == FAILED;
         case SENT -> next == PAID || next == CANCELLED;
         default -> false; // PAID, CANCELLED and FAILED are final
     };
     ```
   - **Task 2 (the door).** Ask the status first, and refuse before anything changes:
     ```java
     public void moveTo(PayoutStatus next) {
         if (!status.canMoveTo(next)) {
             throw new IllegalStateException("A payout cannot move from " + status + " to " + next);
         }
         status = next;
     }
     ```
   - **Task 3 (the call to the bank).** Put the call and the move in a `try`. The method `bank.transfer(payout)` gives back the reference of the bank. In the `catch` for `GatewayException` (only that one), keep the reason, move to FAILED, save, and throw the same exception again, so that the handler of Task 4 can answer. At the end, save the payout as SENT:
     ```java
     try {
         payout.setBankReference(bank.transfer(payout));
         payout.moveTo(PayoutStatus.SENT);
     } catch (GatewayException e) {
         payout.setFailureReason(e.getMessage());
         payout.moveTo(PayoutStatus.FAILED);
         repo.save(payout);
         throw e;
     }
     return repo.save(payout);
     ```
   - **Task 4 (the clear answers).** Copy the style of the handler that is already there. A `ProblemDetail` has `setTitle(...)`. The statuses are `HttpStatus.BAD_GATEWAY` and `HttpStatus.CONFLICT`:
     ```java
     @ExceptionHandler(GatewayException.class)
     public ProblemDetail handleBank(GatewayException e) {
         ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
         problem.setTitle("The bank could not take the payout");
         return problem;
     }

     @ExceptionHandler(IllegalStateException.class)
     public ProblemDetail handleIllegalMove(IllegalStateException e) {
         ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
         problem.setTitle("Not allowed for this payout");
         return problem;
     }
     ```
   - **If something looks wrong.** If every `confirm` gives `409`, Task 1 says `false` for a move that it should allow (check the `SENT` case). If a `confirm` of a `PAID` payout gives `200`, Task 2 is missing. If `POST /payouts` gives `500` when the bank says no, the handler of Task 4 is missing (look at the console for the exception). If the console shows **2 lines** for one failed request, you call the bank twice: the call must be made only once. If the payout is not saved as `FAILED` after a failure, the `catch` of Task 3 does not save it before it throws again. If you see `HTTP 500` with "Transaction ..." words, you put `@Transactional` on `create`: remove it.
