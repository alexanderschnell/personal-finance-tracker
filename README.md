# MyFinancePal

[![Build](https://github.com/alexanderschnell/personal-finance-tracker/actions/workflows/build.yml/badge.svg)](https://github.com/alexanderschnell/personal-finance-tracker/actions/workflows/build.yml)

My personal finance app, built to **track my debts and pay them off**. It's a JavaFX desktop app I use for my own money and keep improving over time.

## Features

**Debts (home screen)**
- Track credit cards and student loans: balance, interest rate, minimum payment and due date.
- Total owing, amount paid off so far, monthly minimums and your **debt-free date** at a glance.
- A progress bar for each debt, plus warnings when a payment is due soon.
- **Log payments.** Interest since the last payment is estimated and split out, so you can see how much actually went to principal.
- **Update balance** to match a statement when new purchases are added or real interest differs from the estimate.
- Full payment history per debt. Paid-off debts move to a "Defeated 🎉" section.

**Payoff plan**
- Set how much you can put toward debt each month.
- Compares **Avalanche** (highest rate first) with **Snowball** (smallest balance first): debt-free date, total interest and payoff order.
- A what-if tip showing how much sooner you'd finish, and how much you'd save, with an extra $100/month.
- A chart of total debt falling to $0 over time.

**Cash flow (secondary):** income and expense transactions with summary charts.

**Local storage:** everything is saved in a local SQLite database. No cloud, no bank logins.

## Running it

Requires **Java 21+**.

**Windows:** double-click `run.cmd`. It builds the app the first time, then starts it. Run `run.cmd rebuild` after changing code.

**Any OS:**

```bash
./mvnw package                                   # build
java -jar target/finance-tracker-1.0-SNAPSHOT.jar
# or during development:
./mvnw javafx:run
```

## Where my data lives

Data is stored **outside the project** so real finances never end up in git:

| OS | Location |
|---|---|
| Windows | `%USERPROFILE%\.myfinancepal\finance.db` |
| macOS / Linux | `~/.myfinancepal/finance.db` |

To try something out without touching real data, point the app at a throwaway database:

```bash
java -Dmyfinancepal.db=test.db -jar target/finance-tracker-1.0-SNAPSHOT.jar
```

Back up `finance.db` by copying it somewhere safe.

## Tech

Java 21 · JavaFX 21 · SQLite (sqlite-jdbc) · Maven

```
src/main/java/com/financetracker/
├── Launcher.java              # entry point for the runnable jar
├── Main.java                  # app shell: header, sidebar, dashboard
├── TransactionManager.java    # SQLite access + change listeners
├── Transaction.java           # model (INCOME / EXPENSE)
├── AddTransactionWindow.java
├── ViewTransactionsWindow.java
├── FinanceDashboard.java      # summary charts
└── ...
```

## Roadmap

- [x] Debts: credit cards and student loans with progress tracking
- [x] Payment logging with an interest/principal split
- [x] Avalanche vs Snowball payoff plan, what-if tip and chart
- [x] Unit tests for the payoff maths and the debt database
- [ ] Interest-free periods (e.g. 0% promo until a date), then the regular rate
- [ ] Minimum payment as a % of balance (how most credit cards calculate it)
- [ ] Payment reminders and a "this month's plan" checklist
- [ ] Milestones: 25 / 50 / 75% paid off celebrations
- [ ] Charts of actual progress vs the plan
- [ ] Import payments from a bank CSV export
- [ ] Export / one-click backup