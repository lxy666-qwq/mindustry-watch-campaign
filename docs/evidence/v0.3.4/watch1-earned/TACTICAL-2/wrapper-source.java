import arc.files.*;
import mindustry.game.*;
import mindustry.io.*;
import mindustry.content.*;
import mindustry.entities.units.*;

/** Native branch combat in an existing genuine QA profile; no progress or resources are injected. */
public class LegalEarnedBranchTrial extends LegalWatchTrial{
    public static void main(String[] args) throws Exception{
        var output = new Fi(args[1]);
        output.mkdirs();
        new Fi("local-work/mindustry-star-work/LegalEarnedBranchTrial.java").copyTo(output.child("wrapper-source.java"));
        if(args[3].equals("watch-1")) preferGraphiteDuo = true;
        if(args[3].equals("watch-1") && args[2].equals("TACTICAL")) planSetup = () -> {
            planDefence();
            var drill = new BuildPlan(83, 34, 0, Blocks.mechanicalDrill);
            queue.add(drill);
            repairPlans.add(drill.copy());
            for(int y = 33; y >= 27; y--){
                var belt = new BuildPlan(83, y, 3, Blocks.conveyor);
                queue.add(belt);
                repairPlans.add(belt.copy());
            }
        };
        finalizeTrial = () -> {
            if(over && winner == Team.sharded) SaveIO.write(output.child("end.msav"));
        };
        LegalWatchTrial.main(args);
    }
}
