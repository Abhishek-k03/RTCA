import { expect, test } from './fixtures'

test('registering signs you in', async ({ page }) => {
  const username = `e2e_reg_${Date.now().toString(36)}`
  await page.goto('/register')
  await page.locator('input[autocomplete=username]').fill(username)
  await page.locator('input[type=email]').fill(`${username}@example.com`)
  await page.locator('input[type=password]').fill('password123')
  await page.locator('input[autocomplete=name]').fill('Nora Quill')
  await page.getByRole('button', { name: 'Create account' }).click()

  await expect(page.getByTitle('Sign out')).toBeVisible()
  await expect(page.locator('footer')).toContainText('Nora Quill')
})

test('a reload keeps you signed in, signing out ends the session', async ({ createUser, signIn }) => {
  const mira = await createUser('Mira Castell')
  const page = await signIn(mira)

  // the access token only lives in memory, the refresh cookie brings it back
  expect(await page.evaluate(() => localStorage.getItem('token'))).toBeNull()
  await page.reload()
  await expect(page.getByTitle('Sign out')).toBeVisible()

  await page.getByTitle('Sign out').click()
  await expect(page.getByRole('button', { name: 'Continue' })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('button', { name: 'Continue' })).toBeVisible()
})

test('wrong password shows an error', async ({ createUser, page }) => {
  const mira = await createUser('Mira Castell')
  await page.goto('/login')
  await page.locator('input[autocomplete=username]').fill(mira.username)
  await page.locator('input[type=password]').fill('not-the-password')
  await page.getByRole('button', { name: 'Continue' }).click()

  await expect(page.getByText('Invalid credentials')).toBeVisible()
})
