/* ==========================================================================
   HavenCraft — Home Page Specific Scripts
   ========================================================================== */

document.addEventListener('DOMContentLoaded', () => {
    const faqItems = document.querySelectorAll('.faq-acc-item');
    if (faqItems.length > 0) {
        faqItems.forEach((item) => {
            item.addEventListener('toggle', () => {
                if (item.open) {
                    faqItems.forEach((other) => {
                        if (other !== item && other.open) {
                            other.open = false;
                        }
                    });
                }
            });
        });
    }
});
