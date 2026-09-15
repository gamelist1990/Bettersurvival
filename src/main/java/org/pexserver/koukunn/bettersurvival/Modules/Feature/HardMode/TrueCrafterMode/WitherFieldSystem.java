package org.pexserver.koukunn.bettersurvival.Modules.Feature.HardMode.TrueCrafterMode;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.pexserver.koukunn.bettersurvival.Loader;

/** projectile.wither_thunderとwither_trap_laserの予告、発動tick、範囲判定を実装する。 */
public final class WitherFieldSystem {
    private final NamespacedKey kind, age, owner;
    private final BukkitTask task;
    public WitherFieldSystem(Loader plugin){kind=new NamespacedKey(plugin,"truecrafter_wither_field");age=new NamespacedKey(plugin,"truecrafter_wither_field_age");owner=new NamespacedKey(plugin,"truecrafter_wither_field_owner");task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,1,1);}
    public void thunder(Wither wither,Location at){spawn(wither,at,"thunder");}
    public void trap(Wither wither,Location at,Player target){Location facing=at.clone();facing.setDirection(target.getEyeLocation().toVector().subtract(facing.toVector()));org.bukkit.util.Vector direction=facing.getDirection().rotateAroundY(java.util.concurrent.ThreadLocalRandom.current().nextDouble(-0.5D,0.5D));facing.setDirection(direction);spawn(wither,facing,"trap");}
    public void shutdown(){task.cancel();}
    private void spawn(Wither wither,Location at,String type){ArmorStand stand=wither.getWorld().spawn(at,ArmorStand.class,s->{s.setInvisible(true);s.setMarker(true);s.setGravity(false);s.setInvulnerable(true);s.setRotation(at.getYaw(),at.getPitch());});stand.getPersistentDataContainer().set(kind,PersistentDataType.STRING,type);stand.getPersistentDataContainer().set(owner,PersistentDataType.STRING,wither.getUniqueId().toString());if(type.equals("thunder"))warningCircle(stand);}
    private void tick(){for(org.bukkit.World w:Bukkit.getWorlds())for(ArmorStand s:w.getEntitiesByClass(ArmorStand.class)){String k=s.getPersistentDataContainer().get(kind,PersistentDataType.STRING);if(k==null)continue;int t=s.getPersistentDataContainer().getOrDefault(age,PersistentDataType.INTEGER,0)+1;s.getPersistentDataContainer().set(age,PersistentDataType.INTEGER,t);if(k.equals("thunder")){if(t==30){pillar(s,10,1,Particle.DUST,Color.fromRGB(224,247,147));s.remove();}}else{if(t<100&&t%5==0)warningBeam(s,Color.fromRGB(179,0,0),1.5F);if(t==100){beam(s,Color.fromRGB(224,247,147),1F);w.playSound(s.getLocation(),org.bukkit.Sound.ENTITY_BREEZE_DEATH,1.0F,1.5F);w.playSound(s.getLocation(),org.bukkit.Sound.ENTITY_WITHER_SKELETON_HURT,1.0F,2.0F);w.playSound(s.getLocation(),org.bukkit.Sound.ENTITY_BLAZE_SHOOT,1.0F,1.5F);}if(t>=100&&t%5==0)laserDamage(s);if(t>=300)s.remove();}}}
    private void warningCircle(ArmorStand stand){Location origin=stand.getLocation().clone().add(0.0D,0.1D,0.0D);for(int index=0;index<40;index++){double angle=Math.PI*2.0D*index/40.0D;Location point=origin.clone().add(Math.cos(angle)*2.0D,0.0D,Math.sin(angle)*2.0D);origin.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,point,1,0.0D,0.0D,0.0D,0.0D,null,true);}}
    private void pillar(ArmorStand s,double damage,double radius,Particle particle,Color color){Location l=s.getLocation();l.getWorld().spawnParticle(Particle.EXPLOSION,l.clone().add(0,20,0),1);l.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION,l.clone().add(0,10,0),200,.2,5,.2,0,new Particle.DustTransition(color,Color.fromRGB(51,51,51),1.0F),true);l.getWorld().spawnParticle(Particle.SMOKE,l.clone().add(0,10,0),200,.4,5,.4,0,null,true);l.getWorld().spawnParticle(Particle.LARGE_SMOKE,l,5,.2,.2,.2,.1D);l.getWorld().playSound(l,org.bukkit.Sound.ENTITY_BLAZE_SHOOT,1.0F,.5F);l.getWorld().playSound(l,org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_IMPACT,.5F,.5F);l.getWorld().playSound(l,org.bukkit.Sound.ENTITY_WITHER_SKELETON_HURT,1.0F,.7F);for(Player p:l.getWorld().getPlayers()){Location pl=p.getLocation();boolean inColumn=Math.abs(pl.getX()-l.getX())<=1.0D&&Math.abs(pl.getZ()-l.getZ())<=1.0D&&pl.getY()>=l.getY()-1.0D&&pl.getY()<=l.getY()+10.0D;if(p.isInvulnerable()||(!inColumn&&pl.distanceSquared(l)>4.0D))continue;p.damage(damage,s);p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER,10,1));}}
    private void beam(ArmorStand s,Color c,float size){Location a=s.getEyeLocation();org.bukkit.util.Vector d=a.getDirection();for(int i=0;i<32;i++){Location p=a.clone().add(d.clone().multiply(i));if(!p.getBlock().isPassable())break;p.getWorld().spawnParticle(Particle.DUST,p,1,0,0,0,0,new Particle.DustOptions(c,size),true);}}
    private void warningBeam(ArmorStand s,Color c,float size){Location a=s.getEyeLocation();org.bukkit.util.Vector d=a.getDirection().normalize();org.bukkit.util.Vector side=new org.bukkit.util.Vector(-d.getZ(),0,d.getX()).normalize();for(double offset:new double[]{-0.5D,0.0D,0.5D})for(int i=0;i<32;i++){Location p=a.clone().add(d.clone().multiply(i)).add(side.clone().multiply(offset));if(!p.getBlock().isPassable())break;p.getWorld().spawnParticle(Particle.DUST,p,1,0,0,0,0,new Particle.DustOptions(c,size),true);if(p.getWorld().getPlayers().stream().anyMatch(player->player.getLocation().distanceSquared(p)<=0.25D))break;}}
    private void laserDamage(ArmorStand s){Location a=s.getEyeLocation();org.bukkit.util.Vector d=a.getDirection();for(int i=0;i<32;i++){Location p=a.clone().add(d.clone().multiply(i));if(!p.getBlock().isPassable())break;for(Player pl:p.getWorld().getPlayers())if(pl.getLocation().distanceSquared(p)<=.5D){pl.damage(3,s);pl.addPotionEffect(new PotionEffect(PotionEffectType.WITHER,40,1));}}}
}
