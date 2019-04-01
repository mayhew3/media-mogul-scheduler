package com.mayhew3.mediamogulscheduler.model;

import com.mayhew3.postgresobject.dataobject.DataObjectMismatch;
import com.mayhew3.postgresobject.db.PostgresConnectionFactory;
import com.mayhew3.postgresobject.db.SQLConnection;
import org.junit.Test;

import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.List;

import static org.junit.Assert.fail;

public class SchemaLocalTest {

  @Test
  public void testLocalSchemaUpToDate() throws URISyntaxException, SQLException {
    SQLConnection connection = PostgresConnectionFactory.getSqlConnection(PostgresConnectionFactory.LOCAL);
    List<DataObjectMismatch> mismatches = MediaMogulSchema.schema.validateSchemaAgainstDatabase(connection);

    if (!mismatches.isEmpty()) {
      System.out.println("Mismatches found: ");
      for (DataObjectMismatch mismatch : mismatches) {
        System.out.println(" - " + mismatch);
        if (mismatch.getMessage().equals("Table not found!")) {
          System.out.println("    - " + mismatch.getDataObject().generateTableCreateStatement());
        }
        if (mismatch.getMessage().equals("ForeignKey restraint not found in DB.")) {
          List<String> stringList = mismatch.getDataObject().generateAddForeignKeyStatements();
          for (String fkStatement : stringList) {
            System.out.println("    - " + fkStatement);
          }
        }
      }
      fail();
    }

  }

}