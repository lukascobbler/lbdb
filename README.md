# LBDB

LBDB - a Java relational database management system implemented for learning purposes. 

## Inspiration

I was greatly inspired by my teaching assistants on the databases university course and began researching databases by myself,
then I stumbled across [this](https://www.youtube.com/watch?v=5Pc18ge9ohI) video which gave me a high-level RDBMS implementation overview
and the motivation to do something similar.

The book [Database Design And Implementation Second Edition](https://link.springer.com/book/10.1007/978-3-030-33836-7)
by [Edward Sciore](https://www.bc.edu/bc-web/schools/morrissey/departments/computer-science/people/faculty-directory/edward-sciore.html)
was the perfect choice for learning the necessary details and my project is based on the system explained in the book. The
book provides a list of tasks that extend functionalities of the system. The overview of task implementations for my project 
can be found in [Tasks.md](Tasks.md).

## Thesis

The first part of this project (chapters up to indexes) represent my Bachelor's thesis 
and can be found [here](). The thesis is only in Serbian.

## Building and running

The system uses `maven` for building, generating artifacts and testing. It is split into three parts: the server, the
shell client and the bulk modifier client. All three have `pom.xml` configurations for being built as artifacts and can be
found in the `target` folder after building.

#### Building artifacts:

`mvn clean package -Dmaven.test.skip=true` 

Skipping tests is recommended for building artifacts. See [Tests](#Tests).

#### Running the server: 

`java -jar LBDBServer.jar db_server 22000`

#### Running the shell client: 

`java -jar LBDBClient.jar 22000`

#### Running the bulk modifier client

`java -jar BulkModifier.jar 22000 example_database/PopulateStatements.sql`

### Example

```sql
LBDB> SELECT * FROM tablecatalog;
LBDB> START TRANSACTION;
LBDB> CREATE TABLE students (
    >   student_name VARCHAR(255) NOT NULL, 
    >   student_index INT NOT NULL,
    >   graduated BOOLEAN NOT NULL,
    >   favourite_subject VARCHAR(255)
    > );
LBDB> INSERT INTO students 
    >   (student_name, student_index, graduated, favourite_subject)
    > VALUES
    >   ('Luka', 100, TRUE, NULL);
LBDB> COMMIT;
LBDB> UPDATE students 
    >   SET favourite_subject = 'DBMS' 
    >   WHERE student_name = 'Luka' AND graduated = TRUE;
LBDB> SELECT favourite_subject FROM students;
```

A full database example can be found in the `example_database/` folder, and can be bootstrapped with the bulk modifier
client (that is its main purpose).

## Tests

[![System tests](https://github.com/lukascobbler/dbms-java/actions/workflows/system-tests.yml/badge.svg?branch=master)](https://github.com/lukascobbler/dbms-java/actions/workflows/system-tests.yml)

There is a (more or less) comprehensive set of tests for every module in the system. Generally, the tests from one module
test only that module and assume components from the lower level modules work as intended. Sometimes, this is not the case
but that is okay.

A custom test "runner" is implemented in [TestUtils.java](src/test/java/com/luka/lbdb/testUtils/TestUtils.java)
that initializes an isolated directory for every test, that other tests won't be able to access.
It has two options for creating directories:
- on disk, in the system's temporary directory; files are preserved after tests finish with running,
but removed before a new test run
- in-memory with [JimFS](https://github.com/google/jimfs); no file is kept after tests finish with running; be wary
because this option can consume a lot of system memory

To run the tests:

`mvn test`

## Limitations

### Limited subset of SQL

The system supports limited functionalities for a limited number of SQL commands:

- `CREATE TABLE` support creating tables with at most 31 fields, with `INT`, `VARCHAR` and `BOOLEAN`
fields and the `NOT NULL` constraint. No primary keys, or other constraints.

- `SELECT` supports running of arbitrary arithmetic expressions in the projection, the `AS` keyword,
range variable names, cross product joins, `UNION ALL`, and predicates.

- `UPDATE` supports a predicate, and a list of new value assignments that can be
arbitrary expressions.

- `INSERT INTO` commands support explicit and implicit fields, **constant** arbitrary
expressions as new values and multiple new tuples per one statement.

- `DELETE` supports a predicate.

- `START TRANSACTION` marks the current client's transaction as manually closeable.

- `COMMIT` commits the current client's transaction, applying all changes from the previous
`START TRANSACTION`.

- `ROLLBACK` rolls back the current client's transaction, revering all changes from the previous
`START TRANSACTION`.

- `EXPLAIN` prints the plan that the system generated (only for `SELECT` statements). 

### Fixed length data

LBDB uses a serial data structure where records are placed one after another, and their offset
can easily be calculated because all records for a given table have the same size. 

This is really simple to implement, but has two main limitations:
- only fixed length data (as stated previously; `VARCHAR` is actually stored as a fixed length field)
- the maximum record size is tied to the system's defined block size, because records
can't span multiple blocks

According to my research, the way to fix this problem is to use the 
[Slotted Page Architecture](https://siemens.blog/posts/database-page-layout/),
which introduces a layer of indirection called the slot number that doesn't change when the page
is moved or resized.

Another decision that must be made is the structure of the file as a whole.
- Heap table file organization is a file organization where data records of a table are stored
in an unordered fashion, placed anywhere within the file (current implementation).
- BTree Index-Organized is a file organization where the records of a table are physically
ordered in the file.

Both options have their advantages and disadvantages.

Heap file table organization (example - PostgreSQL):
- Allows fast inserts, since inserting does not worry about the location of the new record
- Allows transactions to see the system from their perspective, enabling great levels of 
isolation
- Different types of external indexes are supported
- Searching on the primary key requires two accesses
- Disk fragmentation

BTree Index-Organized table organization, also called a Clustered index (example - SQLite):
- Allows the same operations on both external indexes and the row data
- Smaller disk footprint
- Primary key lookup is really fast because record data is in the same place as the 
primary key data
- Inserts may occur reordering of data
- Every table must have a primary key, since data needs to be ordered by something
- Searching on external indexes requires two accesses
- [Reference](https://sqlite.org/forum/info/f07fe501869b2d42)

### Only `AND` predicates

LBDB supports only the `AND` term-joiner meaning predicates can only consist of conditions that can filter
data independently. The SimpleDB book does define some tasks for extending the support for `OR` and `NOT`, but
since these types of predicates aren't independent, I can't estimate how will they integrate later down the line
in the plan optimization and indexing. That is why I decided not to support them for now, but maybe their implementation
will come some day. Note that this is the same reason for why only arithmetic complex expressions are supported.

## Dependencies

- [JLine](https://jline.org/) - pretty client-side terminal handling
- [Apache DataSketches](https://datasketches.apache.org/) - [HyperLogLog](https://en.wikipedia.org/wiki/HyperLogLog.)
implementation for the count-distinct problem
- [JUnit](https://junit.org/) - test framework and runner
- [Mockito](https://site.mockito.org/) - mocking functionalities for test isolation
- [JimFS](https://github.com/google/jimfs) - fast test execution
- [JetBrains Annotations](https://github.com/JetBrains/java-annotations) - better static code analysis

## The future

- Implement indexes
- Implement materialized and aggregate functionalities
- Implement optimized planning with predicate pushdown and complex predicate decision-making
- Implement a variable length file structure
