import arc.files.*;
import mindustry.game.*;
import mindustry.io.*;

/** Native branch combat in an existing genuine QA profile; no progress or resources are injected. */
public class LegalEarnedBranchTrial extends LegalWatchTrial{
    public static void main(String[] args) throws Exception{
        var output = new Fi(args[1]);
        output.mkdirs();
        new Fi("local-work/mindustry-star-work/LegalEarnedBranchTrial.java").copyTo(output.child("wrapper-source.java"));
        finalizeTrial = () -> {
            if(over && winner == Team.sharded) SaveIO.write(output.child("end.msav"));
        };
        LegalWatchTrial.main(args);
    }
}
