package baubles.client.gui;

import baubles.api.IBauble;
import baubles.api.expanded.BaubleExpandedSlots;
import baubles.api.expanded.IBaubleExpanded;
import baubles.common.Baubles;
import baubles.common.BaublesConfig;
import baubles.common.container.ContainerPlayerExpanded;
import baubles.common.container.SlotBauble;
import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.VisiblityData;
import codechicken.nei.api.INEIGuiHandler;
import codechicken.nei.api.TaggedInventoryArea;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.achievement.GuiAchievements;
import net.minecraft.client.gui.achievement.GuiStats;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.IResource;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static baubles.common.BaublesConfig.useOldGuiRendering;
import static net.minecraft.client.gui.inventory.GuiInventory.func_147046_a;

@Optional.Interface(iface = "codechicken.nei.api.INEIGuiHandler", modid = "NotEnoughItems")
public class GuiPlayerExpanded extends GuiContainer implements INEIGuiHandler {

    public static final ResourceLocation background = new ResourceLocation("baubles", "textures/gui/bauble_inventory.png");
    public static final ResourceLocation gui_background = new ResourceLocation("baubles", "textures/gui/bauble_background.png");
    private static final ResourceLocation creative_inventory_tabs = new ResourceLocation("textures/gui/container/creative_inventory/tabs.png");

    private static final boolean hasLwjgl3 = Loader.isModLoaded("lwjgl3ify");

    /**
     * x size of the inventory window in pixels. Defined as float, passed as int.
     */
    private float xSizeFloat;
    /**
     * y size of the inventory window in pixels. Defined as float, passed as int.
     */
    private float ySizeFloat;

    public boolean showActivePotionEffects;

    /** Amount scrolled in inventory (0 = top, 1 = bottom) */
    private float currentScroll;
    /** True if the scrollbar is being dragged */
    private boolean isScrolling;
    /** True if the left mouse button was held down last time drawScreen was called. */
    private boolean wasClicking;

    private int tooltipIndexCache = -1;
    private final List<String> tooltipCache = new ArrayList<>(2);

    public GuiPlayerExpanded(EntityPlayer player) {
        super(new ContainerPlayerExpanded(player.inventory, !player.worldObj.isRemote, player));
        allowUserInput = true;
    }

    /**
     * Called from the main game loop to update the screen.
     */
    @Override
    public void updateScreen() {
        try {
            ((ContainerPlayerExpanded) inventorySlots).baubles.blockEvents = false;
        } catch (Exception ignored) {}
    }

    /**
     * Adds the buttons (and other controls) to the screen in question.
     */
    @Override
    public void initGui() {
        buttonList.clear();
        super.initGui();

        if (!this.mc.thePlayer.getActivePotionEffects().isEmpty() && !useOldGuiRendering) {
            this.showActivePotionEffects = true;
        }
    }

    /**
     * Draws the screen and all the components in it.
     */
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        xSizeFloat = (float) mouseX;
        ySizeFloat = (float) mouseY;

        if (BaublesConfig.displayTooltipOnHover) {
            handleMouseHover(mouseX, mouseY);
        }

        handleScrollbar(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        if (!useOldGuiRendering) {
            this.fontRendererObj.drawString(I18n.format("container.crafting"), 86, 16, 4210752);
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        if (useOldGuiRendering) {
            mc.getTextureManager().bindTexture(background);
        } else {
            mc.getTextureManager().bindTexture(GuiInventory.field_147001_a);
        }

        this.drawBaubleSlots();
        if (showActivePotionEffects) {
            drawPotionEffects();
        }

        // Player model
        func_147046_a(guiLeft + 51, guiTop + 75, 30, (float) (guiLeft + 51) - xSizeFloat, (float) (guiTop + 25) - ySizeFloat, mc.thePlayer);
    }

    /**
     * 绘制饰品槽位的背景和滚动条
     * 根据是否使用旧版GUI渲染来决定绘制方式
     * 在新版GUI中支持多列槽位显示和滚动条
     */
    private void drawBaubleSlots() {
        // 绘制主背景，覆盖整个GUI窗口区域
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
        int bgColor = getPixelRGBA(gui_background, 5, 5);

        // 计算上部高度，基于当前使用的槽位数量
        // 公式：7（顶部边距）+ 槽数量 * 18（每个槽位18像素高）
        int upperHeight = 7 + BaubleExpandedSlots.slotsCurrentlyUsed() * 18;
        final int maxSlotColumn = 8;

        // 根据配置选择纹理，新版GUI使用独立的背景纹理
        if (!useOldGuiRendering) {
            this.mc.getTextureManager().bindTexture(gui_background);
        }

        // 定义槽位偏移量和起始坐标
        final int slotOffset = 18; // 槽位之间的间距为18像素
        final int slotOffsetX = 21; // 列之间的水平间距
        int slotStartX = guiLeft - 26; // 新版GUI中槽位列的起始X坐标（左侧）
        int slotStartY = 12; // 新版GUI中槽位列的起始Y坐标

        // 根据渲染模式设置不同的起始坐标和绘制逻辑
        if (useOldGuiRendering) {
            // 旧版GUI渲染模式：槽位显示在右侧单列
            slotStartX = guiLeft + 79; // 旧版GUI中槽位列的起始X坐标（右侧）
            slotStartY = guiTop + 7;   // 旧版GUI中槽位列的起始Y坐标
        } else {
            // 新版GUI渲染模式处理
            // 计算需要多少列（每列最多5个）
            int slotsCurrentlyUsed = BaubleExpandedSlots.slotsCurrentlyUsed();
            int columnsNeeded = (int) Math.ceil(slotsCurrentlyUsed * 1.0 / (maxSlotColumn * 1.0)); // 向上取整

            if (columnsNeeded <= 1) {
                // 只有一列时，绘制完整的背景
                this.drawTexturedModalRect(this.guiLeft - 26, this.guiTop + 4, 0, 0, 27, upperHeight);
                this.drawTexturedModalRect(this.guiLeft - 26, this.guiTop + 4 + upperHeight, 0, 151, 27, 7);
            } else {
                // 绘制多列背景
                for (int col = 0; col < columnsNeeded; col++) {
                    int columnX = this.guiLeft - 26 - (col * slotOffsetX);
                    int currentUpperHeight = 7 + maxSlotColumn * 18;
                    this.drawTexturedModalRect(columnX, this.guiTop + 4, 0, 0, 27, currentUpperHeight);
                    this.drawTexturedModalRect(columnX, this.guiTop + 4 + currentUpperHeight, 0, 151, 27, 7);

                    // 如果列内槽位数量不足 maxSlotColumn 个，则绘制占位符
                    if (col == columnsNeeded - 1 && slotsCurrentlyUsed % maxSlotColumn != 0) {
                        int noneSlots = slotsCurrentlyUsed % maxSlotColumn;
                        int noneLeft = columnX + 7;
                        int noneTop = 12 + 18 * (maxSlotColumn - slotsCurrentlyUsed / maxSlotColumn);
                        int noneRight = noneLeft + 18;
                        int noneBottom = noneTop + 18 * noneSlots;
                        this.drawGradientRect(noneLeft, noneTop, noneRight, noneBottom, bgColor, bgColor);
                    }
                }
            }
        }

        // 绘制饰品槽位背景并更新槽位位置
        ContainerPlayerExpanded container = (ContainerPlayerExpanded) this.inventorySlots;
        // 遍历所有饰品槽位
        for (int slotIndex = 0; slotIndex < container.getBaubleSlotCount(); slotIndex++) {
            SlotBauble slot = container.getBaubleSlot(slotIndex);
            String slotType = BaubleExpandedSlots.getSlotType(slotIndex);

            // 如果配置不显示未使用槽位且当前槽位类型未知，则跳过绘制
            // 这样可以隐藏玩家当前未解锁或未使用的槽位类型
            if (!BaublesConfig.showUnusedSlots && slotType.equals(BaubleExpandedSlots.unknownType)) {
                continue;
            }

            if (useOldGuiRendering) {
                // 旧版GUI：每列4个槽位的布局
                // X坐标计算：起始X + (槽位索引/4) * 偏移量 （整数除法实现每4个槽位换一列）
                // Y坐标计算：起始Y + (槽位索引%4) * 偏移量 （取模运算实现列内位置）
                drawTexturedModalRect(slotStartX + (slotOffset * (slotIndex / 4)), slotStartY + (slotOffset * (slotIndex % 4)), 200, 0, 18, 18);
            } else {
                // 新版GUI：每列5个槽位的布局
                // 计算当前槽位所在的列和行
                int column = slotIndex / maxSlotColumn; // 列索引（每5个槽位换一列）
                int row = slotIndex % maxSlotColumn;    // 行索引（在一列中的位置）
                // 根据行列计算槽位的实际绘制位置
                // drawTexturedModalRect(slotStartX + (slotOffset * (slotIndex / 4)), slotStartY + (slotOffset * (slotIndex % 4)), 200, 0, 18, 18);
                int xPos = this.guiLeft - (column * slotOffsetX) - 19; // 每增加一列，X坐标向左移动
                int yPos = slotStartY + (slotOffset * (row + 2));    // 每增加一行，Y坐标向下移动
                // 绘制槽位背景纹理（200,0是纹理图中槽位背景的位置）
                drawTexturedModalRect(xPos, yPos, 200, 0, 18, 18);

                // 更新槽位位置以匹配绘制位置，确保鼠标点击和悬停检测区域正确
                // xDisplayPosition和yDisplayPosition是Slot类中用于碰撞检测的字段
                slot.xDisplayPosition = xPos - guiLeft + 1;
                slot.yDisplayPosition = yPos - guiTop + 1;
            }
        }
    }

    public int getPixelRGBA(ResourceLocation rl, int x, int y) {
        try {
            IResource resource = this.mc.getResourceManager().getResource(rl);

            // 读取 PNG 文件为 BufferedImage
            BufferedImage img = ImageIO.read(resource.getInputStream());

            return img.getRGB(x, y);
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }


    private void drawPotionEffects() {
        int slotIndent = 26;
        if (BaubleExpandedSlots.slotsCurrentlyUsed() > 8) {
            slotIndent = 42;
        }
        int positionHorizontal = guiLeft - slotIndent - 124;
        int positionVertical = guiTop;
        Collection<PotionEffect> potionCollection = this.mc.thePlayer.getActivePotionEffects();

        if (potionCollection.isEmpty()) {
            return;
        }

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glDisable(GL11.GL_LIGHTING);
        int maxNumber = 33;

        if (potionCollection.size() > 5) {
            maxNumber = 132 / (potionCollection.size() - 1);
        }

        for (PotionEffect effect : potionCollection) {
            Potion potion = Potion.potionTypes[effect.getPotionID()];
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            this.mc.getTextureManager().bindTexture(field_147001_a);
            this.drawTexturedModalRect(positionHorizontal, positionVertical, 0, 166, 140, 32);

            if (potion.hasStatusIcon()) {
                int potionIconIndex = potion.getStatusIconIndex();
                this.drawTexturedModalRect(positionHorizontal + 6, positionVertical + 7, potionIconIndex % 8 * 18, 198 + potionIconIndex / 8 * 18, 18, 18);
            }

            potion.renderInventoryEffect(positionHorizontal, positionVertical, effect, mc);
            if (!potion.shouldRenderInvText(effect)) continue;
            String potionName = I18n.format(potion.getName());

            if (effect.getAmplifier() >= 1) {
                potionName = potionName + " " + I18n.format("enchantment.level." + effect.getAmplifier());
            }
            this.fontRendererObj.drawStringWithShadow(potionName, positionHorizontal + 10 + 18, positionVertical + 6, 16777215);
            String s = Potion.getDurationString(effect);
            this.fontRendererObj.drawStringWithShadow(s, positionHorizontal + 10 + 18, positionVertical + 6 + 10, 8355711);
            positionVertical += maxNumber;
        }
    }

    private void handleMouseHover(int mouseX, int mouseY) {
        ContainerPlayerExpanded expandedInventory = (ContainerPlayerExpanded) this.inventorySlots;

        // Check the last cached slot first, as it is the most likely one to be hovered out of all of them
        if (tooltipIndexCache != -1) {
            Slot slot = expandedInventory.getBaubleSlot(tooltipIndexCache);

            // Cursor inside slot rect
            if (this.func_146978_c(slot.xDisplayPosition, slot.yDisplayPosition, 16, 16, mouseX, mouseY)) {
                ItemStack stack = expandedInventory.baubles.getStackInSlot(tooltipIndexCache);
                if (stack == null || stack.stackSize == 0) {
                    // drawHoveringText with default font
                    func_146283_a(tooltipCache, mouseX, mouseY);
                    return;
                }
            }
        }

        // Check the other slots
        for (int slotIndex = 0; slotIndex < expandedInventory.getBaubleSlotCount(); slotIndex++) {
            if (slotIndex == tooltipIndexCache) continue;

            Slot slot = expandedInventory.getBaubleSlot(slotIndex);

            // Cursor inside slot rect
            if (!this.func_146978_c(slot.xDisplayPosition, slot.yDisplayPosition, 16, 16, mouseX, mouseY)) continue;

            ItemStack stack = expandedInventory.baubles.getStackInSlot(slotIndex);
            if (stack != null && stack.stackSize > 0) continue; // Only show tooltip on empty slots

            tooltipIndexCache = slotIndex;

            String slotType = BaubleExpandedSlots.getSlotType(slotIndex);

            tooltipCache.clear();

            // Strip formatting codes
            String strippedType = StatCollector.translateToLocal("slot." + slotType).replaceAll("§[0-9a-fklmnor]", "");
            tooltipCache.add(strippedType);

            ItemStack heldItem = mc.thePlayer.inventory.getItemStack();

            if (heldItem != null && heldItem.stackSize > 0) {
                boolean fitsInSlot = false;
                if (heldItem.getItem() instanceof IBaubleExpanded baubleExpandedItem) {
                    String[] itemBaubleTypes = baubleExpandedItem.getBaubleTypes(heldItem);
                    for (String itemBaubleType : itemBaubleTypes) {
                        if (slotType.equals(itemBaubleType)) {
                            fitsInSlot = true;
                            break;
                        }
                    }
                } else if (heldItem.getItem() instanceof IBauble baubleItem) {
                    String itemBaubleType = BaubleExpandedSlots.getTypeFromBaubleType(baubleItem.getBaubleType(heldItem));
                    if (slotType.equals(itemBaubleType)) {
                        fitsInSlot = true;
                    }
                }

                tooltipCache.add(fitsInSlot
                    ? StatCollector.translateToLocal("tooltip.fitsInSlot")
                    : StatCollector.translateToLocal("tooltip.doesNotFitInSlot"));
            }

            // drawHoveringText with default font
            func_146283_a(tooltipCache, mouseX, mouseY);
            return;
        }

        tooltipIndexCache = -1;
    }

    private boolean needsScrollBars() {
        // 不再需要滚动条，因为我们使用多列布局
        return false;
    }

    private void handleScrollbar(int mouseX, int mouseY) {
        // 禁用滚动条处理逻辑，因为我们现在使用多列布局
        return;
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        // 禁用鼠标滚轮滚动功能，因为我们现在使用多列布局
        return;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            mc.displayGuiScreen(new GuiAchievements(this, mc.thePlayer.getStatFileWriter()));
        } else if (button.id == 1) {
            mc.displayGuiScreen(new GuiStats(this, mc.thePlayer.getStatFileWriter()));
        }
    }

    @Override
    protected void keyTyped(char par1, int keyCode) {
        if (keyCode == Baubles.proxy.keyHandler.key.getKeyCode()) {
            mc.thePlayer.closeScreen();
        } else {
            super.keyTyped(par1, keyCode);
        }
    }

    @Override
    protected void handleMouseClick(Slot slotIn, int slotId, int clickedButton, int clickType) {
        if (slotIn != null && clickType == 4 && slotIn.xDisplayPosition < 0 && !useOldGuiRendering) {
            clickType = 0;
        }
        super.handleMouseClick(slotIn, slotId, clickedButton, clickType);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (isClickInUI(mouseX, mouseY)) { // Prevent dropping items when clicking in UI
            return;
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int mouseButton) {
        if (isClickInUI(mouseX, mouseY)) { // Prevent dropping items when clicking in UI
            return;
        }

        super.mouseMovedOrUp(mouseX, mouseY, mouseButton);
    }

    /**
     * Returns true if the mouse is clicked in the scroll bar.
     */
    private boolean isClickInScrollbar(int mouseX, int mouseY) {
        int scrollbarXStart = this.guiLeft - 34;
        int scrollbarYStart = this.guiTop + 12;
        int scrollbarXEnd = scrollbarXStart + 14;
        int scrollbarYEnd = scrollbarYStart + 139;

        return mouseX >= scrollbarXStart && mouseY >= scrollbarYStart &&
            mouseX < scrollbarXEnd && mouseY < scrollbarYEnd;
    }

    /**
     * Returns true if the mouse is clicked in the scrollbar or the surrounding area.
     */
    private boolean isClickInUI(int mouseX, int mouseY) {
        int scrollbarXStart = this.guiLeft - 42;
        int scrollbarYStart = this.guiTop + 5;
        int scrollbarXEnd = scrollbarXStart + 27;
        int scrollbarYEnd = scrollbarYStart + 156;

        return mouseX >= scrollbarXStart && mouseY >= scrollbarYStart &&
            mouseX < scrollbarXEnd && mouseY < scrollbarYEnd;
    }

    @Override
    @Optional.Method(modid = "NotEnoughItems")
    public VisiblityData modifyVisiblity(GuiContainer gui, VisiblityData currentVisibility) {
        return null;
    }

    @Override
    @Optional.Method(modid = "NotEnoughItems")
    public Iterable<Integer> getItemSpawnSlots(GuiContainer gui, ItemStack item) {
        return null;
    }

    @Override
    @Optional.Method(modid = "NotEnoughItems")
    public List<TaggedInventoryArea> getInventoryAreas(GuiContainer gui) {
        return Collections.emptyList();
    }

    @Override
    @Optional.Method(modid = "NotEnoughItems")
    public boolean handleDragNDrop(GuiContainer gui, int mousex, int mousey, ItemStack draggedStack, int button) {
        return false;
    }

    @Override
    @Optional.Method(modid = "NotEnoughItems")
    public boolean hideItemPanelSlot(GuiContainer gui, int slotX, int slotY, int slotW, int slotH) {
        int upperHeight = 7 + BaubleExpandedSlots.slotsCurrentlyUsed() * 18;
        if (!(gui instanceof GuiPlayerExpanded) || useOldGuiRendering) {
            return false;
        }
        int slotIndent = 26;
        int slotWidth = 18;
        if (BaubleExpandedSlots.slotsCurrentlyUsed() > 8) {
            slotIndent = 42;
            slotWidth = 36;
        }
        if (NEIClientConfig.ignorePotionOverlap()) {
            return (new Rectangle4i(guiLeft - slotIndent, guiTop + 4, slotWidth, upperHeight + 4).intersects(new Rectangle4i(slotX, slotY, slotW, slotH)));
        }
        int x = this.guiLeft - 124 - slotIndent;
        int y = this.guiTop;
        Minecraft minecraft = gui.mc;
        if (minecraft == null) {
            return false;
        }
        EntityPlayerSP player = minecraft.thePlayer;
        if (player == null) {
            return false;
        }
        Collection<PotionEffect> activePotionEffects = player.getActivePotionEffects();
        if (activePotionEffects.isEmpty()) {
            return (new Rectangle4i(guiLeft - slotIndent, guiTop + 4, slotWidth, upperHeight + 4).intersects(new Rectangle4i(slotX, slotY, slotW, slotH)));
        }
        int height = 33;
        if (activePotionEffects.size() > 5) {
            height = 132 / (activePotionEffects.size() - 1);
        }
        Rectangle4i slotRect = new Rectangle4i(slotX, slotY, slotW, slotH);
        Rectangle4i baubleSlots = new Rectangle4i(guiLeft - slotIndent, guiTop + 4, slotWidth, upperHeight + 4);
        for (PotionEffect effect : activePotionEffects) {
            Rectangle4i box = new Rectangle4i(x, y, 140, 32);
            box.include(baubleSlots);
            if (box.intersects(slotRect)) return true;
            y += height;
        }
        return false;
    }
}
