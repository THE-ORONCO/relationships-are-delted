package the.oronco.relationshipsaredelted;

import lombok.extern.slf4j.Slf4j;
import org.neo4j.cypherdsl.core.renderer.Configuration;
import org.neo4j.cypherdsl.core.renderer.Dialect;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.neo4j.core.Neo4jTemplate;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.Instant;

@SpringBootApplication
@Slf4j
public class RelationshipsAreDeltedApplication {

    @Bean
    Configuration config() {
        return Configuration.newConfig()
                            .withDialect(Dialect.NEO4J_5)
                            .build();
    }

    public static void main(String[] args) {
        SpringApplication.run(RelationshipsAreDeltedApplication.class, args);
    }

    @Bean
    CommandLineRunner runner(Neo4jTemplate template) {
        return args -> {
            clearDB(template);
            var a = createParentWithChild(template);
            log.warn("=========================================================================");
            log.warn("Initial Parent and child:");
            log.warn("\tParent: {}", template.findAll(Parent.class));
            log.warn("\tChild:  {}", template.findAll(Child.class));
            log.warn("");

            var someChange = template.save(new ShouldDetach(null, new Parent(a.id, null, "UPDATED")));
            log.warn("Saving an incomplete Graph with change propagation turned on behaves as expected and deletes the connection to the child.");
            log.warn("This includes property propagation of the now property which changed from {} to {}", a.prop, someChange.parent.prop);
            log.warn("\tParent      : {}", template.findAll(Parent.class));
            log.warn("\tChild       : {}", template.findAll(Child.class));
            log.warn("\tParentParent: {}", template.findAll(ShouldDetach.class));
            log.warn("");



            clearDB(template);
            var b = createParentWithChild(template);
            template.save(new ShouldNotDetach(null, new Parent(b.id, null, "UPDATED")));
            var noChangeLoaded = template.findAll(ShouldNotDetach.class).getFirst();
            log.warn("Saving an incomplete Graph with change propagation turned off does not propagate the properties as the now property did not change. before: {} after: {}.", b.prop, noChangeLoaded.parent.prop);
            log.error("However the Relationships still got saved even though the change propagation should have stopped this!");
            log.error("The Parent lost their child: {}", noChangeLoaded.parent);
            log.warn("\tParent      : {}", template.findAll(Parent.class));
            log.warn("\tChild       : {}", template.findAll(Child.class));
            log.warn("\tParentParent: {}", template.findAll(ShouldNotDetach.class));
            log.warn("=========================================================================");
            System.exit(1);
        };
    }

    private static Parent createParentWithChild(Neo4jTemplate template) {
        return template.save(new Parent(null, new Child(null), "INITIAL"));
    }

    private static void clearDB(Neo4jTemplate template) {
        template.deleteAll(Parent.class);
        template.deleteAll(Child.class);
        template.deleteAll(ShouldDetach.class);
        template.deleteAll(ShouldNotDetach.class);
    }

    @Node
    record Parent(@Id @GeneratedValue String id, @Relationship(cascadeUpdates = false) Child child, String prop) {}

    @Node
    record Child(@Id @GeneratedValue String id) {}

    @Node
    record ShouldDetach(@Id @GeneratedValue String id, @Relationship(cascadeUpdates = true) Parent parent) {}
    @Node
    record ShouldNotDetach(@Id @GeneratedValue String id, @Relationship(cascadeUpdates = false) Parent parent) {}

}
