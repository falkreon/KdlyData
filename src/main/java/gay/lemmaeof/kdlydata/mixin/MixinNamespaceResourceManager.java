package gay.lemmaeof.kdlydata.mixin;

import gay.lemmaeof.kdlydata.Hooks;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.resource.NamespaceResourceManager;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceIoSupplier;
import net.minecraft.resource.ResourceMetadata;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.pack.ResourcePack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

@Mixin(NamespaceResourceManager.class)
public abstract class MixinNamespaceResourceManager {
	private static final Identifier kdlydata$JSON_TEST = new Identifier("kdlydata", "a/path/to/a.json");
	private static final Identifier kdlydata$NOT_JSON_TEST = new Identifier("kdlydata", "not/a/path/to/a/json.txt");
	private static final Predicate<Identifier> kdlydata$KDLY_FILTER = id -> id.getPath().endsWith(".kdl");

	@Shadow @Final protected List<NamespaceResourceManager.PackEntry> packs;
	@Shadow @Final private ResourceType type;

	@Shadow protected abstract ResourceIoSupplier<ResourceMetadata> getMetadataReader(Identifier path, int packIndex);

	@Shadow @Final private static Logger LOGGER;

	@Shadow static Identifier getMetadataPath(Identifier id) {
		throw new UnsupportedOperationException("illegal");
	}

	@Shadow @Final private String namespace;
	private final ThreadLocal<ResourcePack> kdlydata$resourcePack = new ThreadLocal<>();
	private final ThreadLocal<Integer> kdlydata$packIndex = new ThreadLocal<>();

	@Inject(method = "getResource", at = @At(value = "INVOKE", target = "net/minecraft/resource/NamespaceResourceManager$PackEntry.isExcludedFromLowerPriority(Lnet/minecraft/util/Identifier;)Z"), cancellable = true, locals = LocalCapture.CAPTURE_FAILEXCEPTION)
	private void hookGetResource(Identifier id, CallbackInfoReturnable<Optional<Resource>> info, int i) {
		if (!id.getPath().endsWith(".json")) return;
		Identifier kdlId = new Identifier(id.getNamespace(), id.getPath().replace(".json", ".kdl")); //TODO: Chop 4 chars off the end and append "kdl" instead of replacing all occurrences
		NamespaceResourceManager.PackEntry packEntry = this.packs.get(i);
		ResourcePack resourcePack = packEntry.pack();
		
		if (resourcePack != null && Hooks.hasResource(resourcePack, this.type, kdlId)) {
			try {
				ResourceIoSupplier<InputStream> kdlyResourceSupplier = Hooks.getKdlyInputStreamSupplier((NamespaceResourceManagerAccessor) (Object) this, kdlId, resourcePack);
				
				info.setReturnValue(Optional.of(
						new Resource(resourcePack, kdlyResourceSupplier, this.getMetadataReader(id, i))
				));
			} catch (IOException | IllegalArgumentException e) {
				LOGGER.warn("Resource {} failed to process from KDL: {}", kdlId, e);
			}
		}
	}

	@Inject(method = "getAllResources", at = @At("TAIL"), cancellable = true)
	private void hookGetAllResources(Identifier id, CallbackInfoReturnable<List<Resource>> info) {
		List<Resource> ret = new ArrayList<>(info.getReturnValue());
		List<NamespaceResourceManager.ResourceEntry> found = new ArrayList<>();

		Identifier kdlId = new Identifier(id.getNamespace(), id.getPath().replace(".json", ".kdl")); //TODO: Chop 4 chars off the end and append "kdl" instead of replacing all occurrences
		Identifier metaId = getMetadataPath(id);
		String string = null;

		for(NamespaceResourceManager.PackEntry packEntry : this.packs) {
			if (packEntry.isExcludedFromLowerPriority(kdlId)) {
				if (!found.isEmpty()) {
					string = packEntry.name();
				}

				found.clear();
			} else if (packEntry.isExcludedFromLowerPriority(metaId)) {
				//TODO: THIS IS A MAJOR POINT OF CONFLICT
				//found.forEach(NamespaceResourceManager.ResourceEntry::markMetadataAsAbsent);
			}

			ResourcePack resourcePack = packEntry.pack();
			if (resourcePack != null && Hooks.hasResource(resourcePack, this.type, kdlId)) {
				try {
					ResourceIoSupplier<InputStream> supplier = Hooks.getKdlyInputStreamSupplier((NamespaceResourceManagerAccessor) (Object) this, kdlId, resourcePack);
					NamespaceResourceManager.ResourceEntry entry = ResourceEntryAccessor.invokeInit(resourcePack, supplier);
					NamespaceResourceManager.ResourceEntries entries = ResourceEntriesAccessor.invokeInit(kdlId, metaId, new ArrayList<>(), new HashMap<>());
					
					//Hooks.entryAsKdlyResource(this, entries, entry);
				} catch (IOException ex) {
					ex.printStackTrace();
					return;
				}
				//TODO: ANOTHER POINT OF CONFLICT
				//found.add(ResourceEntryAccessor.invokeInit((NamespaceResourceManager) (Object) this, kdlId, metaId, resourcePack));
			}
		}

		if (found.isEmpty() && string != null) {
			LOGGER.info("Resource {} was filtered by pack {}", id, string);
		}

		for (NamespaceResourceManager.ResourceEntry entry : found) {
			//TODO: THIS IS A MAJOR POINT OF CONFLICT
			
			//ret.add(Hooks.entryAsKdlyResource((NamespaceResourceManagerAccessor) (Object) this, found, entry));
		}
		info.setReturnValue(ret);
	}

	@Inject(method = "findResources", at = @At("HEAD"))
	private void detectSearchingForJson(String startingPath, Predicate<Identifier> pathFilter, CallbackInfoReturnable<Map<Identifier, Resource>> info) {
		Hooks.predicateIsJson.set(pathFilter.test(kdlydata$JSON_TEST) && !pathFilter.test(kdlydata$NOT_JSON_TEST));
	}

	@Inject(method = "findResources", at = @At(value = "INVOKE", target = "net/minecraft/resource/pack/ResourcePack.findResources(Lnet/minecraft/resource/ResourceType;Ljava/lang/String;Ljava/lang/String;Ljava/util/function/Predicate;)Ljava/util/Collection;"), locals = LocalCapture.CAPTURE_FAILEXCEPTION)
	private void appendKdlFiles(String startingPath, Predicate<Identifier> pathFilter, CallbackInfoReturnable<Map<Identifier, Resource>> info, Object2IntMap<Identifier> foundIds, int totalPacks, int i, NamespaceResourceManager.PackEntry entry) {
		if (Hooks.predicateIsJson.get()) {
			entry.pack().listResources(this.type, this.namespace, startingPath, (id, supplier) -> {
				if (kdlydata$KDLY_FILTER.test(id)) foundIds.put(id, i);
			});
		}
	}

	@Inject(method = "findResources", at = @At(value = "INVOKE", target = "java/util/Map.put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"), locals = LocalCapture.CAPTURE_FAILEXCEPTION)
	private void cacheResourcePack(String startingPath, Predicate<Identifier> pathFilter, CallbackInfoReturnable<Map<Identifier, Resource>> cir, Object2IntMap<Identifier> foundIds, int totalPacks, Map<Identifier, Resource> ret, ObjectIterator<Identifier> iter, Object2IntMap.Entry<Identifier> entry, int packIndex, Identifier id, ResourcePack pack) {
		if (Hooks.predicateIsJson.get() && id.getPath().endsWith(".kdl")) {
			kdlydata$resourcePack.set(pack);
			kdlydata$packIndex.set(packIndex);
		}
	}

	@ModifyArgs(method = "findResources", at = @At(value = "INVOKE", target = "java/util/Map.put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
	private void transformKdlId(Args args) {
		if (Hooks.predicateIsJson.get()) {
			Identifier id = args.get(0);
			if (id.getPath().endsWith(".kdl")) {
				Identifier jsonishId = new Identifier(id.getNamespace(), id.getPath().replace(".kdl", ".json"));
				ResourcePack pack = kdlydata$resourcePack.get();
				args.set(0, jsonishId);
				try {
					args.set(1, new Resource(pack, Hooks.getKdlyInputStreamSupplier((NamespaceResourceManagerAccessor) this, id, pack), this.getMetadataReader(jsonishId, kdlydata$packIndex.get())));
				} catch (IOException | IllegalArgumentException e) {
					throw new RuntimeException(e);
				}
			}
		}
	}

	@Inject(method = "findAllResources", at = @At("HEAD"))
	private void detectSearchingForJsonAll(String startingPath, Predicate<Identifier> pathFilter, CallbackInfoReturnable<Map<Identifier, List<Resource>>> info) {
		Hooks.predicateIsJson.set(pathFilter.test(kdlydata$JSON_TEST) && !pathFilter.test(kdlydata$NOT_JSON_TEST));
	}

	@Inject(method = "findResourcesOf", at = @At("TAIL"))
	private void appendKdlFilesAll(NamespaceResourceManager.PackEntry pack, String startingPath, Predicate<Identifier> pathFilter, Map<Identifier, NamespaceResourceManager.ResourceEntries> resources, CallbackInfo info) {
		if (Hooks.predicateIsJson.get()) {
			ResourcePack resourcePack = pack.pack();
			if (resourcePack != null) {
				resourcePack.listResources(this.type, this.namespace, startingPath, (id, accessor) -> {
					
					if (!kdlydata$KDLY_FILTER.test(id)) return;
					
					Identifier jsonishId = new Identifier(id.getNamespace(), id.getPath().replace(".kdl", ".json"));
					Identifier metaId = getMetadataPath(jsonishId);
					
					NamespaceResourceManager.ResourceEntries resEntries = resources.computeIfAbsent(jsonishId, identifier2x -> ResourceEntriesAccessor.invokeInit(jsonishId, metaId, new ArrayList<>(), new HashMap<>()));
					NamespaceResourceManager.ResourceEntry resEntry = ResourceEntryAccessor.invokeInit(pack.pack(), accessor);
					resEntries.fileSources().add(resEntry);
				});
			}
		}
	}

	@Inject(method = "findAllResources", at = @At(value = "INVOKE", target = "java/util/Map.forEach(Ljava/util/function/BiConsumer;)V"))
	private void cacheResourceManager(String startingPath, Predicate<Identifier> pathFilter, CallbackInfoReturnable<Map<Identifier, List<Resource>>> info) {
		Hooks.thisManager.set((NamespaceResourceManagerAccessor) this);
	}
	
	@Mixin(NamespaceResourceManager.ResourceEntries.class)
	private static abstract class MixinResourceEntries {
		
		@Shadow @Final private List<NamespaceResourceManager.ResourceEntry> fileSources;
		
		
		@Shadow abstract Identifier path();
		@Shadow abstract Identifier metadataId();
		
		@Inject(method = "fileSources", at = @At("HEAD"), cancellable = true)
		private void hookKdlResources(CallbackInfoReturnable<List<NamespaceResourceManager.ResourceEntry>> info) {
			if (Hooks.predicateIsJson.get()) {
				List<NamespaceResourceManager.ResourceEntry> ret = new ArrayList<>();
				
				
				for (NamespaceResourceManager.ResourceEntry entry : this.fileSources) {
					
					if (this.path().getPath().endsWith(".kdl")) {
						ret.add(Hooks.entryAsKdlyEntry(Hooks.thisManager.get(), (NamespaceResourceManager.ResourceEntries) (Object) this, entry));
					} else {
						ret.add(entry);
					}
				}
				info.setReturnValue(ret);
			}
		}
	}
	
	
	@Mixin(NamespaceResourceManager.ResourceEntries.class)
	private static interface ResourceEntriesAccessor {
		@Invoker("<init>")
		static NamespaceResourceManager.ResourceEntries invokeInit(
				Identifier path,
				Identifier metadataId,
				List<NamespaceResourceManager.ResourceEntry> fileSources,
				Map<ResourcePack, ResourceIoSupplier<InputStream>> metadataSources
				) {
			throw new AssertionError("impossible");
		}
	}

	
	@Mixin(NamespaceResourceManager.ResourceEntry.class)
	public static interface ResourceEntryAccessor {
		
		@Invoker("<init>")
		static NamespaceResourceManager.ResourceEntry invokeInit(ResourcePack source, ResourceIoSupplier<InputStream> resource) {
			throw new AssertionError("impossible");
		}
	}

	@Mixin(NamespaceResourceManager.PackEntry.class)
	private static class MixinPackEntry {
		@Shadow @Final private @Nullable Predicate<Identifier> filter;

		@Inject(method = "isExcludedFromLowerPriority", at = @At("HEAD"), cancellable = true)
		private void injectExclusion(Identifier id, CallbackInfoReturnable<Boolean> info) {
			if (id.getPath().endsWith(".kdl")) {
				Identifier jsonishId = new Identifier(id.getNamespace(), id.getPath().replace(".kdl", ".json"));
				if (this.filter != null && this.filter.test(jsonishId)) info.setReturnValue(true);
			}
		}
	}
}
