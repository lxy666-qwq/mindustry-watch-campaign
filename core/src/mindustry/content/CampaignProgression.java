package mindustry.content;

import arc.*;
import arc.struct.*;
import mindustry.content.CampaignStars.*;
import mindustry.game.*;
import mindustry.game.Objectives.*;
import mindustry.type.*;
import mindustry.world.*;

/** The optional Watch branches become required preparation at specific counterplay milestones. */
public class CampaignProgression{
    public static int requiredReward(SectorPreset preset, Mode mode){
        if(CampaignStars.branchIndex(preset) >= 0) return -1;
        return switch(preset.name){
            case "frozenForest" -> 0;
            case "ruinousShores", "biomassFacility" -> 1;
            case "stainedMountains" -> 2;
            case "windsweptIslands" -> 3;
            case "impact0078" -> 4;
            case "navalFortress" -> 5;
            case "ravine", "peaks" -> 6;
            case "stronghold" -> 7;
            case "crevice" -> 8;
            case "origin", "planetaryTerminal" -> mode == Mode.NORMAL ? -1 : 9;
            default -> -1;
        };
    }

    public static boolean allowed(SectorPreset preset, Mode mode){
        int reward = requiredReward(preset, mode);
        return reward < 0 || CampaignStars.completedReward(reward);
    }

    public static SectorPreset primaryParent(SectorPreset preset){
        int branch = CampaignStars.branchIndex(preset);
        if(branch >= 0) return WatchCampaign.anchors[branch];
        var node = preset.techNode.parent;
        while(node != null){
            if(node.content instanceof SectorPreset parent && CampaignStars.eligible(parent)) return parent;
            node = node.parent;
        }
        return null;
    }

    public static Seq<SectorPreset> parents(SectorPreset preset){
        var parents = new Seq<SectorPreset>();
        var parent = primaryParent(preset);
        if(parent != null) parents.add(parent);
        if(CampaignStars.branchIndex(preset) < 0){
            for(var objective : preset.techNode.objectives){
                if(objective instanceof SectorComplete capture && CampaignStars.eligible(capture.preset) && !parents.contains(capture.preset)) parents.add(capture.preset);
            }
        }
        return parents;
    }

    /** Use the host map's actual equipment family: Watch branches retain Serpulo equipment on either planet. */
    public static Seq<Block> rewardSet(int index, boolean erekir){
        if(erekir){
            return switch(index){
                case 0 -> Seq.with(Blocks.breach, WatchRewardBlocks.fieldBandage);
                case 1 -> Seq.with(Blocks.diffuse);
                case 2 -> Seq.with(Blocks.ventCondenser, Blocks.reinforcedConduit);
                case 3 -> Seq.with(Blocks.diffuse, Blocks.shipFabricator);
                case 4 -> Seq.with(Blocks.disperse);
                case 5 -> Seq.with(Blocks.sublimate, Blocks.reinforcedPump);
                case 6 -> Seq.with(Blocks.sublimate, Blocks.reinforcedPump);
                case 7 -> Seq.with(Blocks.afflict, Blocks.heatRouter);
                case 8 -> Seq.with(Blocks.lustre, Blocks.regenProjector);
                case 9 -> Seq.with(Blocks.unitRepairTower);
                default -> throw new IllegalArgumentException("Unknown Watch reward: " + index);
            };
        }
        return switch(index){
            case 0 -> Seq.with(Blocks.scatter, WatchRewardBlocks.fieldBandage);
            case 1 -> Seq.with(Blocks.arc, Blocks.lancer);
            case 2 -> Seq.with(Blocks.wave, Blocks.mechanicalPump, Blocks.conduit);
            case 3 -> Seq.with(Blocks.hail, Blocks.ripple);
            case 4 -> Seq.with(Blocks.swarmer, Blocks.segment);
            case 5 -> Seq.with(Blocks.tsunami, Blocks.airFactory);
            case 6 -> Seq.with(Blocks.mendProjector);
            case 7 -> Seq.with(Blocks.batteryLarge);
            case 8 -> Seq.with(Blocks.foreshadow, Blocks.forceProjector);
            case 9 -> Seq.with(Blocks.repairPoint);
            default -> throw new IllegalArgumentException("Unknown Watch reward: " + index);
        };
    }

    public static String rewardEquipment(int index, boolean erekir){
        return rewardSet(index, erekir).toString(", ", b -> b.localizedName);
    }

    public static String requirements(SectorPreset preset, Mode mode){
        boolean cn = Core.bundle.getLocale().getLanguage().equals("zh");
        StringBuilder text = new StringBuilder();
        for(var parent : parents(preset)){
            if(text.length() > 0) text.append("; ");
            text.append(parent.localizedName).append(cn ? "普通通关" : " normal completion");
        }
        int branch = CampaignStars.branchIndex(preset);
        if(branch >= 0){
            if(text.length() > 0) text.append("; ");
            text.append(cn ? "主线总星数≥" : "Main stars ≥ ").append(WatchCampaign.starRequirements[branch]);
        }
        int reward = requiredReward(preset, mode);
        if(reward >= 0){
            if(text.length() > 0) text.append("; ");
            text.append(WatchCampaign.sectors[reward].localizedName).append(cn ? "满五星，获得：" : " five stars, grants: ");
            text.append(rewardEquipment(reward, preset.planet == Planets.erekir));
            if(preset == SectorPresets.stronghold){
                text.append(cn ? "；本关新任务提供巨浪合金100，供建造一座劫难，读档不补发" : "; this new mission includes 100 surge alloy for one Afflict; no refill on reload");
            }
        }
        if(mode != Mode.NORMAL){
            if(text.length() > 0) text.append("; ");
            text.append(preset.localizedName).append(cn ? "普通通关" : " normal completion");
        }
        return text.length() == 0 ? (cn ? "起始关卡，无前置" : "Starting mission, no prerequisite") : text.toString();
    }

    /** Only a full-star branch grants persistent equipment, never a main-level score. */
    public static void applyRewards(Rules rules){
        if(!rules.tags.containsKey(CampaignStars.prefix + "preset") || rules.pvp || rules.editor || mindustry.Vars.net.active()) return;
        //Stronghold has no slag or scrap: its unlocked Afflict needs a finite construction kit.
        if(rules.tags.get(CampaignStars.prefix + "preset").equals("stronghold") && CampaignStars.completedReward(7) &&
            !rules.tags.getBool(CampaignStars.prefix + "counterKit")){
            rules.loadout.add(new ItemStack(Items.surgeAlloy, 100));
            rules.tags.put(CampaignStars.prefix + "counterKit", "true");
        }
        boolean erekir = rules.planet == Planets.erekir && CampaignStars.branchIndex(mindustry.Vars.content.sector(rules.tags.get(CampaignStars.prefix + "preset"))) < 0;
        for(int index = 0; index < WatchCampaign.sectors.length; index++){
            if(!CampaignStars.completedReward(index)) continue;
            for(var block : rewardSet(index, erekir)){
                rules.researched.add(block);
                if(rules.blockWhitelist) rules.bannedBlocks.add(block);
                else rules.bannedBlocks.remove(block);
            }
        }
    }
}
